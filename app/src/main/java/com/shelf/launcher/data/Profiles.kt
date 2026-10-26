package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Update 332: who is playing — Voltcap's profiles ("Who's playing?" at start, Settings › Profiles, R1 › Who is playing).
 *
 * Each profile has its own name and picture (its RetroAchievements picture, the Steam picture, one of your own, or its
 * initial), its own favorites, Continue playing / last played and play time, and can have its own RetroAchievements
 * account (one of the two the login keeps, RaCredentialsStore). The library, saves, artwork and settings are shared.
 *
 * The first profile (id 0) is everything Voltcap had before profiles: its files keep their old names ("favorites",
 * "recently_played", "playtime") and it is the one Google Drive syncs; another profile's are the same files with
 * "__p<id>" after the name ([fileFor]). The list and the one playing are plain prefs ("profiles").
 */
object Profiles {
    private const val PREFS = "profiles"
    private const val KEY_LIST = "list_json"
    private const val KEY_ACTIVE = "active"
    const val MAX = 8

    enum class Picture { RA, STEAM, OWN, INITIAL }

    data class Profile(
        val id: Int,
        val name: String,
        val picture: Picture = Picture.INITIAL,
        /** Its RetroAchievements account: the login's slot 0 or 1, or none. */
        val raSlot: Int? = null,
        /** A hue (0–360) for its initial's circle and its glow. */
        val hue: Float = 210f,
        /** Update 334: its 4-digit PIN, hashed (null: no lock). */
        val pinHash: String? = null
    ) {
        val locked: Boolean get() = pinHash != null
    }

    /** The profiles, in order (never empty once [ensure] ran), and the one playing — live for the screens. */
    val list = mutableStateOf<List<Profile>>(emptyList())
    val activeId = mutableStateOf(0)

    @Volatile private var loaded = false

    /** The prefs file [base] of the profile playing (the first profile's is [base] itself). */
    fun fileFor(base: String): String = fileFor(base, activeId.value)

    fun fileFor(base: String, id: Int): String = if (id == 0) base else "${base}__p$id"

    fun active(): Profile? = list.value.firstOrNull { it.id == activeId.value } ?: list.value.firstOrNull()

    /**
     * Reads the profiles once. The first time ever, the first profile is made from what Voltcap knew (the
     * RetroAchievements account in use, else "Player"), and a second saved RetroAchievements account becomes a second
     * profile — "Who is playing" picked between those two before.
     */
    fun ensure(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var all = parse(prefs.getString(KEY_LIST, null))
            if (all.isEmpty()) {
                val names = runCatching { RaCredentialsStore(context).usernames() }.getOrDefault(listOf(null, null))
                all = buildList {
                    add(Profile(0, names[0] ?: "Player", if (names[0] != null) Picture.RA else Picture.INITIAL, if (names[0] != null) 0 else null, 210f))
                    names.getOrNull(1)?.let { add(Profile(1, it, Picture.RA, 1, 28f)) }
                }
                prefs.edit().putString(KEY_LIST, toJson(all)).apply()
            }
            list.value = all
            // (Before profiles: the one whose RetroAchievements account was in use is the one playing.)
            val raNow = runCatching { RaCredentialsStore(context).activeSlot() }.getOrDefault(0)
            val first = if (prefs.contains(KEY_ACTIVE)) prefs.getInt(KEY_ACTIVE, 0) else all.firstOrNull { it.raSlot == raNow }?.id ?: 0
            activeId.value = first.takeIf { id -> all.any { it.id == id } } ?: all.first().id
            loaded = true
        }
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, toJson(list.value)).putInt(KEY_ACTIVE, activeId.value).apply()
    }

    /** Makes [id] the profile playing (its files from now on). Returns it, or null when there's no such profile. */
    fun setActive(context: Context, id: Int): Profile? {
        ensure(context)
        val p = list.value.firstOrNull { it.id == id } ?: return null
        activeId.value = id
        save(context)
        return p
    }

    /** A new profile named [name]; null when there are [MAX] already. */
    fun add(context: Context, name: String): Profile? {
        ensure(context)
        if (list.value.size >= MAX) return null
        val id = (list.value.maxOfOrNull { it.id } ?: 0) + 1
        val hues = listOf(210f, 28f, 140f, 280f, 350f, 180f, 50f, 250f)
        val p = Profile(id, name.trim().ifBlank { "Player ${list.value.size + 1}" }, Picture.INITIAL, null, hues[list.value.size % hues.size])
        list.value = list.value + p
        save(context)
        return p
    }

    /** The RetroAchievements slots other profiles than [id] use. */
    fun raSlotsTaken(context: Context, id: Int = activeId.value): Set<Int> {
        ensure(context)
        return list.value.filter { it.id != id }.mapNotNull { it.raSlot }.toSet()
    }

    fun update(context: Context, p: Profile) {
        ensure(context)
        list.value = list.value.map { if (it.id == p.id) p else it }
        save(context)
    }

    /**
     * Forgets profile [id] (never the first one): its favorites, last played and play time files and its picture go;
     * its RetroAchievements account stays saved in the login (another profile can take it).
     */
    fun remove(context: Context, id: Int) {
        ensure(context)
        if (id == 0 || list.value.none { it.id == id }) return
        list.value = list.value.filterNot { it.id == id }
        if (activeId.value == id) activeId.value = 0
        save(context)
        for (base in PER_PROFILE) runCatching { context.applicationContext.deleteSharedPreferences(fileFor(base, id)) }
        runCatching { pictureFile(context, id).delete() }
    }

    /** Update 334: [pin]'s hash for profile [id] (never stored as it is). */
    fun hashPin(id: Int, pin: String): String = runCatching {
        java.security.MessageDigest.getInstance("SHA-256").digest("voltcap-profile:$id:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.getOrDefault(pin.hashCode().toString())

    /** Whether [pin] opens profile [p] (any PIN for one without a lock). */
    fun pinOpens(p: Profile, pin: String): Boolean = p.pinHash == null || p.pinHash == hashPin(p.id, pin)

    /** Sets (a 4-digit [pin]) or takes away (null) profile [id]'s lock. */
    fun setPin(context: Context, id: Int, pin: String?) {
        ensure(context)
        val p = list.value.firstOrNull { it.id == id } ?: return
        update(context, p.copy(pinHash = pin?.takeIf { it.length == 4 && it.all(Char::isDigit) }?.let { hashPin(id, it) }))
    }

    /** The files each profile has its own of. */
    val PER_PROFILE = listOf("favorites", "recently_played", "playtime")

    /** Where profile [id]'s own picture is kept. */
    fun pictureFile(context: Context, id: Int): java.io.File =
        java.io.File(java.io.File(context.filesDir, "profiles").apply { mkdirs() }, "p$id.webp")

    /** Copies the picture at [uri] (cut square, 384 px) as profile [id]'s own; true when it worked. */
    fun setPicture(context: Context, id: Int, uri: android.net.Uri): Boolean = runCatching {
        val src = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return false
        val side = minOf(src.width, src.height)
        val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = android.graphics.Bitmap.createScaledBitmap(sq, 384, 384, true)
        pictureFile(context, id).outputStream().use {
            @Suppress("DEPRECATION")
            out.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, it)
        }
        list.value.firstOrNull { it.id == id }?.let { update(context, it.copy(picture = Picture.OWN)) }
        pictureStamp.value++
        true
    }.getOrDefault(false)

    /** Bumped when a picture changes, so the screens load it again. */
    val pictureStamp = mutableStateOf(0)

    /**
     * The picture to show for [p]: its own file, its RetroAchievements picture ([raNames]: the login's two user names),
     * the Steam picture ([steamAvatar]), or null (its initial).
     */
    fun pictureModel(context: Context, p: Profile, raNames: List<String?>, steamAvatar: String?): Any? = when (p.picture) {
        Picture.OWN -> pictureFile(context, p.id).takeIf { it.isFile }
        Picture.RA -> p.raSlot?.let { raNames.getOrNull(it) }?.let { "https://media.retroachievements.org/UserPic/$it.png" }
        Picture.STEAM -> steamAvatar
        Picture.INITIAL -> null
    }

    private fun toJson(all: List<Profile>): String = JSONArray().apply {
        all.forEach { p ->
            put(JSONObject().put("id", p.id).put("name", p.name).put("pic", p.picture.name).put("hue", p.hue.toDouble())
                .apply { p.raSlot?.let { put("ra", it) }; p.pinHash?.let { put("pin", it) } })
        }
    }.toString()

    private fun parse(json: String?): List<Profile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Profile(
                    o.getInt("id"), o.optString("name", "Player"),
                    runCatching { Picture.valueOf(o.optString("pic", "INITIAL")) }.getOrDefault(Picture.INITIAL),
                    if (o.has("ra")) o.getInt("ra").coerceIn(0, 1) else null,
                    o.optDouble("hue", 210.0).toFloat(),
                    o.optString("pin", "").ifBlank { null }
                )
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
}
