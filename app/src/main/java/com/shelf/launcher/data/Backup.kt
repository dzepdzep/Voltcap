package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A stable, non-secret id for this physical device + app signing key, used to tell "same device" from "new device". */
object DeviceIdentity {
    fun fingerprint(context: Context): String {
        val raw = runCatching {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty() + "|" + android.os.Build.MODEL
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}

/** What a backup file says about itself (read without applying anything). */
data class BackupInfo(
    val formatVersion: Int,
    val appVersion: String?,
    val sourceModel: String?,
    val created: Long,
    val hasGameData: Boolean,
    val hasDeviceSetup: Boolean,
    /** False when the file came from another device (or this one has a different id). */
    val sameDevice: Boolean,
    /** Services that were signed in on the source device (they must be signed in again here). */
    val signedInServices: List<String>
)

/** What an import did. */
data class ImportReport(
    val filesApplied: Int,
    val deviceSetupApplied: Boolean,
    val deviceSetupSkipped: Boolean,
    val snapshotId: String?,
    /** Services to sign in to again on this device. */
    val needsSignIn: List<String>
)

data class SnapshotInfo(val id: String, val created: Long, val files: Int, val reason: String)

/**
 * Moves Shelf's settings to another device (or back) as one JSON file.
 *
 * Only settings travel: the look and behaviour ([SettingsScope.GLOBAL]),
 * optionally this device's folders/emulators/screens ([SettingsScope.DEVICE]).
 * Sign-ins, tokens and bookkeeping never go in a file and are never restored
 * (see SettingsScopes.kt) — after moving, services are signed in again by hand.
 * Downloaded images aren't included; they download again on their own.
 *
 * Importing first saves the current settings as a snapshot (last 5 kept),
 * checks the whole file, and only then applies it; if applying fails midway
 * the snapshot is put back, so a half-restored state is never left behind.
 */
object Backup {
    private const val FORMAT = 2
    private const val KEEP_SNAPSHOTS = 5
    private const val ENCRYPTED_MARKER = "__androidx_security_crypto_encrypted_prefs"

    /**
     * Per-game data, keyed by each game's file path: details, artwork
     * choices, favourites, playtime, achievement matches. Only useful on a
     * device with the same games in the same places, so it's left out of a
     * backup unless asked for — and an import without it leaves this
     * device's own game data alone.
     */
    internal val GAME_DATA = setOf(
        "metadata_cache", "art_cache", "art_overrides", "game_overrides", "favorites", "playtime",
        "recently_played", "ra_matches", "screenshots", "screenshots_v2", "video_urls", "steam_games"
    )

    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    private fun snapshotDir(context: Context) = AppStorage.dir(context, "Settings snapshots", File(context.filesDir, "settings_snapshots"))

    /** Every prefs file that could ever travel (AUTH and INTERNAL files are filtered out). */
    private fun names(context: Context): List<String> =
        prefsDir(context).listFiles().orEmpty()
            .filter { it.name.endsWith(".xml") }
            .map { it.name.removeSuffix(".xml") }
            .filter { SettingsScopes.scopeOf(it).let { s -> s == SettingsScope.GLOBAL || s == SettingsScope.DEVICE } }
            .filterNot { isEncrypted(context, it) }

    private fun isEncrypted(context: Context, name: String): Boolean =
        runCatching { File(prefsDir(context), "$name.xml").readText().contains(ENCRYPTED_MARKER) }.getOrDefault(false)

    // ---- Serialising ----

    private fun entryOf(value: Any?): JSONObject? = when (value) {
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is String -> JSONObject().put("t", "s").put("v", value)
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }))
        else -> null
    }

    /** [names] as the "files" object; device keys are left out unless [withDevice]. */
    private fun serialize(context: Context, names: Collection<String>, withDevice: Boolean): JSONObject {
        val files = JSONObject()
        names.forEach { name ->
            val scope = SettingsScopes.scopeOf(name)
            if (scope != SettingsScope.GLOBAL && scope != SettingsScope.DEVICE) return@forEach
            if (scope == SettingsScope.DEVICE && !withDevice) return@forEach
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach entries@{ (key, value) ->
                if (key.startsWith(ENCRYPTED_MARKER)) return@entries
                if (SettingsScopes.scopeOf(name, key) == SettingsScope.DEVICE && !withDevice) return@entries
                entryOf(value)?.let { values.put(key, it) }
            }
            files.put(name, JSONObject().put("scope", scope.name).put("values", values))
        }
        return files
    }

    private fun appVersion(context: Context): String? =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

    private fun manifest(context: Context, withDevice: Boolean, gameData: Boolean, reason: String, signedIn: List<String>) =
        JSONObject()
            .put("formatVersion", FORMAT)
            .put("appVersion", appVersion(context) ?: "")
            .put("deviceModel", android.os.Build.MODEL)
            .put("deviceId", DeviceIdentity.fingerprint(context))
            .put("scopes", JSONArray().put("GLOBAL").apply { if (withDevice) put("DEVICE") })
            .put("gameData", gameData)
            .put("reason", reason)
            .put("signedIn", JSONArray(signedIn))

    private fun document(context: Context, files: JSONObject, manifest: JSONObject, withDevice: Boolean, gameData: Boolean, accounts: JSONObject? = null) =
        JSONObject()
            .put("app", "Voltcap")
            .put("format", FORMAT)
            .put("created", System.currentTimeMillis())
            .put("gameData", gameData)
            .put("manifest", manifest)
            .put("files", files)
            .also { if (withDevice) it.put("deviceSetup", true) }
            .also { if (accounts != null && accounts.length() > 0) it.put("accounts", accounts) }

    /**
     * Writes the backup to [uri]. Returns how many settings files went in.
     * Sign-ins only with [includeAccounts]. [includeDeviceSetup] adds this device's folders,
     * emulator setup and screen layouts.
     */
    fun export(context: Context, uri: Uri, includeGameData: Boolean = false, includeDeviceSetup: Boolean = false, includeAccounts: Boolean = false): Int {
        val chosen = names(context).filter { includeGameData || it !in GAME_DATA }
        val files = serialize(context, chosen, includeDeviceSetup)
        val root = document(
            context, files,
            manifest(context, includeDeviceSetup, includeGameData, "export", signedInServices(context)),
            includeDeviceSetup, includeGameData, if (includeAccounts) serializeAccounts(context) else null
        )
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString().toByteArray()) }
            ?: error("Couldn't write the file.")
        return files.length()
    }

    /**
     * The backup as text, for sending somewhere other than a file (Google Drive, another Voltcap
     * on the same Wi-Fi). Sign-ins only with [includeAccounts]; game data stays out (Drive syncs it on its own).
     * This device's setup goes along but is only applied when it comes back to this same device.
     */
    fun exportText(context: Context, reason: String = "transfer", includeDeviceSetup: Boolean = true, includeAccounts: Boolean = false): String {
        val chosen = names(context).filter { it !in GAME_DATA }
        val files = serialize(context, chosen, includeDeviceSetup)
        return document(
            context, files,
            manifest(context, includeDeviceSetup, false, reason, signedInServices(context)),
            includeDeviceSetup, false, if (includeAccounts) serializeAccounts(context) else null
        ).toString()
    }

    /** Where Export saves by default: Internal storage › Voltcap › Voltcap settings sync (one file, replaced each time, easy to sync). */
    fun syncFolder(): File = File(AppStorage.rootFolder(), "Voltcap settings sync")
    fun syncFile(): File = File(syncFolder(), "voltcap-backup.json")

    /** Saves the backup into [syncFile] (needs All files access on Android 11+). Returns how many settings files went in. */
    fun exportToSyncFolder(context: Context, includeGameData: Boolean, includeDeviceSetup: Boolean, includeAccounts: Boolean = false): Int {
        val dir = syncFolder()
        if (!dir.isDirectory && !dir.mkdirs()) error("Couldn't create the folder Voltcap/Voltcap settings sync.")
        // Written next to it first, then swapped in: a sync app never picks up half a file.
        val tmp = File(dir, ".voltcap-backup.json.part")
        val n = exportTo(context, tmp, includeGameData, includeDeviceSetup, includeAccounts)
        val target = syncFile()
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        return n
    }

    /** Same as [export], into a plain file (the visible Voltcap folder's settings copy). */
    fun exportTo(context: Context, file: File, includeGameData: Boolean, includeDeviceSetup: Boolean, includeAccounts: Boolean = false): Int {
        val chosen = names(context).filter { includeGameData || it !in GAME_DATA }
        val files = serialize(context, chosen, includeDeviceSetup)
        val root = document(
            context, files,
            manifest(context, includeDeviceSetup, includeGameData, "mirror", signedInServices(context)),
            includeDeviceSetup, includeGameData, if (includeAccounts) serializeAccounts(context) else null
        )
        file.writeText(root.toString(2))
        return files.length()
    }

    // ---- Reading / validating ----

    private class Staged(
        val name: String,
        val values: Map<String, JSONObject>,
        val hasDeviceKeys: Boolean,
        /** Keys the player chose to leave alone (Import Preview): neither replaced nor removed. */
        val skip: ((String, String) -> Boolean)? = null
    )

    private fun parse(context: Context, uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't read the file.")
        return parseText(text)
    }

    /** Reads and checks a backup at [uri] (nothing is applied). */
    internal fun readDocument(context: Context, uri: Uri): JSONObject = parse(context, uri)

    /** Checks backup text (from Drive or another device); nothing is applied. */
    internal fun parseDocument(text: String): JSONObject = parseText(text)

    /** What a checked backup says about itself. */
    internal fun describeDocument(context: Context, root: JSONObject): BackupInfo = describe(context, root)

    /**
     * Exactly what an import of [root] would write, file → (key → typed entry), after sign-ins,
     * bookkeeping and (unless [importDevice]) this device's own settings are dropped. Used by the
     * Import Preview, so what it lists is what gets applied.
     */
    internal fun plan(root: JSONObject, importDevice: Boolean): Map<String, Map<String, JSONObject>> =
        stage(root, importDevice, linkedSetOf()).associate { it.name to it.values }

    private fun parseText(text: String): JSONObject {
        val root = try { JSONObject(text) } catch (e: Exception) { error("That isn't a Voltcap backup.") }
        require(root.optString("app") in setOf("Voltcap", "Shelf")) { "That isn't a Voltcap backup." }
        require(root.optInt("format", 1) <= FORMAT) { "This backup was made by a newer Voltcap. Update the app first." }
        require(root.optJSONObject("files") != null) { "That backup has no settings in it." }
        return root
    }

    /** Backup text, checked (throws a readable message when it isn't one). For the Import Preview. */
    fun readDocument(text: String): JSONObject = parseText(text)

    /** Reads what a backup contains without applying anything. */
    fun inspect(context: Context, uri: Uri): BackupInfo = describe(context, parse(context, uri))

    private fun describe(context: Context, root: JSONObject): BackupInfo {
        val m = root.optJSONObject("manifest")
        val files = root.getJSONObject("files")
        var device = root.optBoolean("deviceSetup", false)
        files.keys().forEach { n ->
            if (SettingsScopes.scopeOf(n) == SettingsScope.DEVICE) device = true
            else if (SettingsScopes.hasDeviceKeys(n)) {
                val values = files.optJSONObject(n)?.optJSONObject("values")
                values?.keys()?.forEach { k -> if (SettingsScopes.isDeviceKey(n, k)) device = true }
            }
        }
        val id = m?.optString("deviceId").orEmpty()
        val signed = m?.optJSONArray("signedIn")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return BackupInfo(
            formatVersion = root.optInt("format", 1),
            appVersion = m?.optString("appVersion")?.takeIf { it.isNotBlank() },
            sourceModel = m?.optString("deviceModel")?.takeIf { it.isNotBlank() },
            created = root.optLong("created", 0L),
            hasGameData = root.optBoolean("gameData", false),
            hasDeviceSetup = device,
            // Old files (no manifest) carry no device id: treated as this device, as they always were.
            sameDevice = id.isEmpty() || id == DeviceIdentity.fingerprint(context),
            signedInServices = signed
        )
    }

    /**
     * Checks the whole file and turns it into ready-to-apply pieces. Throws
     * on the first problem, before anything on the device has changed.
     * AUTH and INTERNAL files are dropped (their services are collected in [ignoredAuth]).
     */
    private fun stage(
        root: JSONObject, importDevice: Boolean, ignoredAuth: MutableSet<String>,
        skip: ((String, String) -> Boolean)? = null
    ): List<Staged> {
        val files = root.getJSONObject("files")
        val out = ArrayList<Staged>()
        files.keys().forEach { name ->
            val scope = SettingsScopes.scopeOf(name)
            val raw = files.optJSONObject(name)
            if (scope == SettingsScope.AUTH || raw?.optBoolean("encrypted") == true) {
                // Old backups carried decrypted credentials: never restored, only noted.
                val filled = raw?.optJSONObject("values")?.length() ?: 0
                if (filled > 0) SettingsScopes.serviceOf(name)?.let { ignoredAuth.add(it) }
                return@forEach
            }
            if (scope == SettingsScope.INTERNAL) return@forEach
            if (scope == SettingsScope.DEVICE && !importDevice) return@forEach
            val file = files.optJSONObject(name) ?: error("The backup is damaged ($name).")
            val values = file.optJSONObject("values") ?: error("The backup is damaged ($name).")
            val kept = LinkedHashMap<String, JSONObject>()
            var deviceKeys = false
            var skipped = 0
            values.keys().forEach { key ->
                if (key.startsWith(ENCRYPTED_MARKER)) return@forEach
                if (skip?.invoke(name, key) == true) { skipped++; return@forEach }
                val isDeviceKey = SettingsScopes.scopeOf(name, key) == SettingsScope.DEVICE
                if (isDeviceKey && scope != SettingsScope.DEVICE) {
                    if (!importDevice) return@forEach
                    deviceKeys = true
                }
                val entry = values.optJSONObject(key) ?: error("The backup is damaged ($name › $key).")
                validate(entry, "$name › $key")
                kept[key] = entry
            }
            // Everything of this file was left out on purpose: don't touch the file at all.
            if (skipped > 0 && kept.isEmpty()) return@forEach
            out.add(Staged(name, kept, deviceKeys, skip))
        }
        return out
    }

    private fun validate(entry: JSONObject, where: String) {
        try {
            when (entry.getString("t")) {
                "b" -> entry.getBoolean("v")
                "i" -> entry.getInt("v")
                "l" -> entry.getLong("v")
                "f" -> entry.getDouble("v")
                "s" -> entry.getString("v")
                "set" -> entry.getJSONArray("v").let { a -> (0 until a.length()).forEach { a.getString(it) } }
                else -> error("unknown type")
            }
        } catch (e: Exception) {
            error("The backup is damaged ($where).")
        }
    }

    // ---- Applying ----

    /** Current keys of [name] that the backup replaces: everything, except this device's own keys in mixed files. */
    private fun replaceable(prefs: SharedPreferences, s: Staged): Set<String> {
        val scope = SettingsScopes.scopeOf(s.name)
        val keys = if (scope == SettingsScope.DEVICE || !SettingsScopes.hasDeviceKeys(s.name) || s.hasDeviceKeys) prefs.all.keys
        else prefs.all.keys.filter { SettingsScopes.scopeOf(s.name, it) != SettingsScope.DEVICE }.toSet()
        val skip = s.skip ?: return keys
        return keys.filterNot { skip(s.name, it) }.toSet()
    }

    private fun edit(context: Context, s: Staged): SharedPreferences.Editor {
        val prefs = context.getSharedPreferences(s.name, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        replaceable(prefs, s).forEach { edit.remove(it) }
        s.values.forEach { (key, entry) ->
            when (entry.getString("t")) {
                "b" -> edit.putBoolean(key, entry.getBoolean("v"))
                "i" -> edit.putInt(key, entry.getInt("v"))
                "l" -> edit.putLong(key, entry.getLong("v"))
                "f" -> edit.putFloat(key, entry.getDouble("v").toFloat())
                "s" -> edit.putString(key, entry.getString("v"))
                "set" -> {
                    val array = entry.getJSONArray("v")
                    edit.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
            }
        }
        return edit
    }

    /** Commits every staged file; on any failure puts back what [undo] captured and rethrows. */
    private fun applyAll(context: Context, staged: List<Staged>, undo: List<Staged>) {
        val editors = staged.map { it to edit(context, it) } // everything built (and so checked) before the first commit
        val done = ArrayList<String>()
        try {
            editors.forEach { (s, e) ->
                if (!e.commit()) error("Couldn't save ${s.name}.")
                done.add(s.name)
            }
        } catch (e: Exception) {
            undo.filter { it.name in done || it.name == editors.getOrNull(done.size)?.first?.name }.forEach { u ->
                runCatching { edit(context, u).commit() }
            }
            throw IllegalStateException("Couldn't apply the backup, so nothing was changed. (${e.message})", e)
        }
    }

    /** What the touched files look like now, as staged pieces (files absent today come back empty). */
    private fun currentOf(context: Context, staged: List<Staged>): List<Staged> =
        staged.map { s ->
            val values = LinkedHashMap<String, JSONObject>()
            var deviceKeys = false
            context.getSharedPreferences(s.name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                if (k.startsWith(ENCRYPTED_MARKER)) return@forEach
                if (SettingsScopes.scopeOf(s.name, k) == SettingsScope.DEVICE && SettingsScopes.scopeOf(s.name) != SettingsScope.DEVICE) deviceKeys = true
                entryOf(v)?.let { values[k] = it }
            }
            Staged(s.name, values, deviceKeys || s.hasDeviceKeys)
        }

    private fun writeSnapshot(context: Context, staged: List<Staged>, reason: String): String {
        val dir = snapshotDir(context).apply { mkdirs() }
        val files = JSONObject()
        currentOf(context, staged).forEach { s ->
            val values = JSONObject()
            s.values.forEach { (k, v) -> values.put(k, v) }
            files.put(s.name, JSONObject().put("scope", SettingsScopes.scopeOf(s.name).name).put("values", values))
        }
        val root = document(context, files, manifest(context, true, true, reason, emptyList()), true, true)
        val id = "snapshot-${System.currentTimeMillis()}"
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(root.toString())
        check(tmp.renameTo(File(dir, "$id.json"))) { "Couldn't save a safety copy of the current settings." }
        dir.listFiles().orEmpty().filter { it.name.endsWith(".json") }.sortedByDescending { it.name }
            .drop(KEEP_SNAPSHOTS).forEach { it.delete() }
        dir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") && it != tmp }.forEach { it.delete() }
        return id
    }

    /**
     * Replaces this device's settings with the backup at [uri]. Returns how
     * many settings files were applied. [importDeviceSetup]: null (default)
     * applies the device-specific part only when the backup came from this
     * device; true/false force it.
     */
    fun import(context: Context, uri: Uri, importDeviceSetup: Boolean? = null): Int =
        importDetailed(context, uri, importDeviceSetup).filesApplied

    fun importDetailed(context: Context, uri: Uri, importDeviceSetup: Boolean? = null): ImportReport =
        applyDocument(context, parse(context, uri), importDeviceSetup, "before import")

    /**
     * Applies a checked backup. [skip] (file, key) → true leaves that setting exactly as it is on
     * this device (the groups unticked in the Import Preview). A safety snapshot is taken first.
     */
    fun applyDocument(
        context: Context, root: JSONObject, importDeviceSetup: Boolean?, reason: String,
        skip: ((String, String) -> Boolean)? = null,
        /** Also sign in to the accounts the backup carries (when it has any). */
        importAccounts: Boolean = true
    ): ImportReport {
        val info = describe(context, root)
        val device = importDeviceSetup ?: info.sameDevice
        val ignoredAuth = linkedSetOf<String>()
        val staged = stage(root, device, ignoredAuth, skip)
        val hasDevicePart = info.hasDeviceSetup
        // Safety copy of exactly what is about to be replaced; if it can't be made, nothing changes.
        val snapshot = writeSnapshot(context, staged, reason)
        applyAll(context, staged, readSnapshot(context, snapshot))
        if (importAccounts) applyAccounts(context, root)
        val signedNow = signedInServices(context).toSet()
        val need = (info.signedInServices + ignoredAuth).distinct().filter { it !in signedNow }
        need.forEach { SecureStore.flagRelogin(context, it) }
        return ImportReport(staged.size, device && hasDevicePart, !device && hasDevicePart, snapshot, need)
    }

    // ---- Snapshots ----

    /** Automatic safety copies, newest first. Reads files: call off the main thread. */
    fun listSnapshots(context: Context): List<SnapshotInfo> =
        snapshotDir(context).listFiles().orEmpty().filter { it.name.endsWith(".json") }
            .sortedByDescending { it.name }.mapNotNull { f ->
                runCatching {
                    val root = JSONObject(f.readText())
                    SnapshotInfo(
                        f.name.removeSuffix(".json"), root.optLong("created", f.lastModified()),
                        root.getJSONObject("files").length(),
                        root.optJSONObject("manifest")?.optString("reason").orEmpty()
                    )
                }.getOrNull()
            }

    /** Puts a snapshot back (after taking a fresh one, so the restore itself can be undone). */
    fun restoreSnapshot(context: Context, id: String): ImportReport {
        require(id.matches(Regex("snapshot-\\d+"))) { "Unknown snapshot." }
        val file = File(snapshotDir(context), "$id.json")
        val root = parseText(file.readText())
        return applyDocument(context, root, true, "before restoring a snapshot")
    }

    private fun readSnapshot(context: Context, id: String): List<Staged> =
        stage(parseText(File(snapshotDir(context), "$id.json").readText()), true, linkedSetOf())

    // ---- Accounts (only when asked: "Include accounts") ----

    /**
     * The services whose sign-in is a key or a login that works on any device (encrypted store, its
     * service name, older plain names). Google Drive (Play Services), Spotify and Discord sign in per
     * device and can't be carried over: those still ask to sign in again.
     */
    private val ACCOUNT_STORES = listOf(
        Triple("ra_credentials_encrypted", "RetroAchievements", listOf("ra_credentials")),
        Triple("sgdb_settings_encrypted", "SteamGridDB", listOf("sgdb_settings")),
        Triple("igdb_credentials_encrypted", "IGDB", listOf("igdb_credentials")),
        Triple("tgdb_credentials_encrypted", "TheGamesDB", listOf("tgdb_credentials")),
        Triple("screenscraper_credentials_encrypted", "ScreenScraper", listOf("screenscraper_credentials")),
        Triple("rawg_encrypted", "RAWG", listOf("rawg")),
        Triple("romm_credentials_encrypted", "RomM", listOf("romm_credentials")),
        Triple("metadata_source_encrypted", "Metadata source", listOf("metadata_source"))
    )

    /**
     * Sign-ins kept in plain prefs that work on any device: Steam (a Steam Web API key and a SteamID, nothing
     * that rotates). Discord and Spotify refresh tokens rotate (two devices sharing one would log each other
     * out) and Google Drive is tied to this device's Google account, so those three always sign in per device.
     */
    private val PLAIN_ACCOUNT_STORES = listOf("steam_account" to "Steam")

    /** Services that can never travel in a file; each device signs in to them itself. */
    val PER_DEVICE_SERVICES = listOf("Google Drive", "Discord", "Spotify")

    /** The accounts as {"store": {"service": …, "values": {key: entry}}}. The file then holds keys: keep it private. */
    private fun serializeAccounts(context: Context): JSONObject {
        val app = context.applicationContext
        val out = JSONObject()
        PLAIN_ACCOUNT_STORES.forEach { (file, service) ->
            runCatching {
                val values = JSONObject()
                app.getSharedPreferences(file, Context.MODE_PRIVATE).all.forEach { (k, v) -> entryOf(v)?.let { values.put(k, it) } }
                if (values.length() > 0) out.put(file, JSONObject().put("service", service).put("values", values))
            }
        }
        ACCOUNT_STORES.forEach { (file, service, plain) ->
            runCatching {
                val values = JSONObject()
                SecureStore.open(app, file, service, plain).prefs.all.forEach { (k, v) ->
                    if (!k.startsWith(ENCRYPTED_MARKER)) entryOf(v)?.let { values.put(k, it) }
                }
                if (values.length() > 0) out.put(file, JSONObject().put("service", service).put("values", values))
            }
        }
        return out
    }

    /** Which services a backup carries sign-ins for. */
    fun accountServices(root: JSONObject): List<String> {
        val a = root.optJSONObject("accounts") ?: return emptyList()
        return a.keys().asSequence().mapNotNull { a.optJSONObject(it)?.optString("service")?.takeIf { s -> s.isNotBlank() } }.toList()
    }

    /** Writes the backup's accounts into this device's encrypted stores. Returns the services restored. */
    private fun applyAccounts(context: Context, root: JSONObject): List<String> {
        val a = root.optJSONObject("accounts") ?: return emptyList()
        val app = context.applicationContext
        val done = ArrayList<String>()
        a.keys().forEach { file ->
            val plain = PLAIN_ACCOUNT_STORES.firstOrNull { it.first == file }
            val store = ACCOUNT_STORES.firstOrNull { it.first == file }
                ?: plain?.let { Triple(it.first, it.second, emptyList<String>()) } ?: return@forEach
            val values = a.optJSONObject(file)?.optJSONObject("values") ?: return@forEach
            runCatching {
                val e = if (plain != null) app.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
                    else SecureStore.open(app, store.first, store.second, store.third).prefs.edit()
                values.keys().forEach { k ->
                    val entry = values.getJSONObject(k)
                    when (entry.getString("t")) {
                        "b" -> e.putBoolean(k, entry.getBoolean("v"))
                        "i" -> e.putInt(k, entry.getInt("v"))
                        "l" -> e.putLong(k, entry.getLong("v"))
                        "f" -> e.putFloat(k, entry.getDouble("v").toFloat())
                        "s" -> e.putString(k, entry.getString("v"))
                        "set" -> e.putStringSet(k, entry.getJSONArray("v").let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() })
                    }
                }
                e.commit()
                done += store.second
                SecureStore.clearRelogin(app, store.second)
            }
        }
        return done
    }

    // ---- Sign-ins (names only, never values) ----

    /** Which services are signed in on this device right now. Names only. */
    fun signedInServices(context: Context): List<String> {
        val app = context.applicationContext
        fun has(file: String, service: String, key: String, plain: List<String> = emptyList()) =
            runCatching { SecureStore.open(app, file, service, plain).prefs.getString(key, null) != null }.getOrDefault(false)
        val out = ArrayList<String>()
        if (has("ra_credentials_encrypted", "RetroAchievements", "api_key", listOf("ra_credentials"))) out.add("RetroAchievements")
        if (has("sgdb_settings_encrypted", "SteamGridDB", "api_key", listOf("sgdb_settings"))) out.add("SteamGridDB")
        if (has("igdb_credentials_encrypted", "IGDB", "client_id", listOf("igdb_credentials"))) out.add("IGDB")
        if (has("tgdb_credentials_encrypted", "TheGamesDB", "api_key", listOf("tgdb_credentials"))) out.add("TheGamesDB")
        if (has("screenscraper_credentials_encrypted", "ScreenScraper", "dev_id", listOf("screenscraper_credentials"))) out.add("ScreenScraper")
        if (has("rawg_encrypted", "RAWG", "key", listOf("rawg"))) out.add("RAWG")
        if (has("romm_credentials_encrypted", "RomM", "server", listOf("romm_credentials"))) out.add("RomM")
        if (has("discord_encrypted", "Discord", "refresh_token", listOf("discord"))) out.add("Discord")
        if (runCatching {
                app.getSharedPreferences("steam_account", Context.MODE_PRIVATE).getString("key", null) != null
            }.getOrDefault(false)
        ) out.add("Steam")
        if (runCatching {
                app.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).getString("settings_json", null)
                    ?.contains("\"connected\":true") == true
            }.getOrDefault(false)
        ) out.add("Google Drive")
        return out
    }

    /** Starts Shelf afresh so every part reloads the restored settings. */
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

/** Whether the first-start setup has been shown (or wasn't needed). */
object FirstRun {
    private fun prefs(context: Context) = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)

    /** Existing installs (a ROM folder already chosen) skip it. */
    fun isDone(context: Context): Boolean =
        prefs(context).getBoolean("done", false) ||
            context.getSharedPreferences("library_settings", Context.MODE_PRIVATE).contains("root_path")

    fun markDone(context: Context) = prefs(context).edit().putBoolean("done", true).apply()
}
