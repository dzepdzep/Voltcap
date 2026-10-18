package com.shelf.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * Update 117: play from your other devices (synced through Google Drive with favourites and playtime), so the recaps
 * count the games you played on the other handheld too — also games that aren't on this device.
 *
 * Each device puts its own play sessions (the most recent ones) into the synced launcher data under its own id; this
 * device keeps the other devices' sessions here, their game keys already turned into this device's (CloudSync).
 */
object OtherDevices {
    private const val PREFS = "other_devices"
    private const val KEY = "sessions_json"
    /** How many of this device's sessions go up (the newest). */
    const val SHARE_MAX = 1500

    /** The name and console of a game played on another device, and that device's name. */
    data class Info(val name: String, val platform: String, val device: String)

    /** This device's own id in the synced data (made once). */
    @Synchronized
    fun deviceId(context: Context): String {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString("device_id", null)
        // Update 144: the id is kept with a mark of this very handheld (Android's own id for this app). Older versions
        // copied the id to a second handheld with Settings transfer, and two devices with one id wiped each other's play
        // from the recaps. An id without this device's mark (a copy, or one made before this update) is made new once;
        // the old one is remembered so the next sync takes its entry off Google Drive (retiredIds).
        val mark = deviceMark(app)
        if (saved != null && (mark == null || p.getString("device_mark", null) == mark)) return saved
        val id = java.util.UUID.randomUUID().toString()
        val e = p.edit().putString("device_id", id).putString("device_mark", mark)
        if (saved != null) e.putStringSet("retired_ids", (retiredIds(app) + saved).toList().takeLast(10).toSet())
        e.apply()
        return id
    }

    /** Update 144: ids this device used before ([deviceId]); their entries are dropped from the synced data. */
    fun retiredIds(context: Context): Set<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("retired_ids", emptySet()).orEmpty().toSet()

    private fun deviceMark(app: Context): String? = runCatching {
        android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * This device's entry: {"label": model, "s": [[key, start, ms, name, platform], …]} with portable keys
     * ([portable]); [info] gives a game's name and console from the library.
     */
    fun ownEntry(context: Context, portable: (String) -> String, info: ((String) -> Pair<String, String>?)?): String {
        val sessions = runCatching { PlaytimeStore(context.applicationContext).loadSessions() }.getOrDefault(emptyList())
            .sortedByDescending { it.start }.take(SHARE_MAX)
        // The names and consoles are remembered here, so a sync in the background (no library loaded) sends the same.
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = runCatching { JSONObject(prefs.getString("names_json", null) ?: "{}") }.getOrDefault(JSONObject())
        var changed = false
        val arr = JSONArray()
        sessions.forEach { s ->
            val fresh = info?.invoke(s.key)
            val saved = known.optJSONArray(s.key)?.let { it.optString(0) to it.optString(1) }
            if (fresh != null && fresh != saved) { known.put(s.key, JSONArray().put(fresh.first).put(fresh.second)); changed = true }
            val (name, platform) = fresh ?: saved ?: (s.key.substringAfterLast('/').substringBeforeLast('.') to "")
            arr.put(JSONArray().put(portable(s.key)).put(s.start).put(s.ms).put(name).put(platform))
        }
        if (changed) prefs.edit().putString("names_json", known.toString()).apply()
        return JSONObject().put("label", android.os.Build.MODEL ?: "Another device").put("s", arr).toString()
    }

    /** Keeps the other devices' entries (their keys turned into this device's with [local]). */
    fun store(context: Context, entries: Map<String, String>, local: (String) -> String) {
        val out = JSONObject()
        entries.forEach { (id, text) ->
            runCatching {
                val o = JSONObject(text)
                val src = o.optJSONArray("s") ?: JSONArray()
                val arr = JSONArray()
                for (i in 0 until src.length()) {
                    val e = src.optJSONArray(i) ?: continue
                    arr.put(JSONArray().put(local(e.optString(0))).put(e.optLong(1)).put(e.optLong(2)).put(e.optString(3)).put(e.optString(4)))
                }
                out.put(id, JSONObject().put("label", o.optString("label")).put("s", arr))
            }
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply()
    }

    private fun read(context: Context): JSONObject =
        runCatching { JSONObject(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "{}") }
            .getOrDefault(JSONObject())

    /** Every session played on the other devices (this device's keys). */
    fun sessions(context: Context): List<PlaytimeStore.Session> {
        val o = read(context)
        val out = ArrayList<PlaytimeStore.Session>()
        o.keys().forEach { id ->
            val arr = o.optJSONObject(id)?.optJSONArray("s") ?: return@forEach
            for (i in 0 until arr.length()) {
                val e = arr.optJSONArray(i) ?: continue
                val ms = e.optLong(2)
                if (ms > 0) out += PlaytimeStore.Session(e.optString(0), e.optLong(1), ms)
            }
        }
        return out
    }

    /** Update 220: each other device's name (its model), by its id. */
    fun labels(context: Context): Map<String, String> {
        val o = read(context)
        val out = HashMap<String, String>()
        o.keys().forEach { id -> out[id] = o.optJSONObject(id)?.optString("label").orEmpty().ifBlank { "Another device" } }
        return out
    }

    /** Name, console and device of each game played on the other devices (by this device's key). */
    fun info(context: Context): Map<String, Info> {
        val o = read(context)
        val out = HashMap<String, Info>()
        o.keys().forEach { id ->
            val d = o.optJSONObject(id) ?: return@forEach
            val label = d.optString("label").ifBlank { "Another device" }
            val arr = d.optJSONArray("s") ?: return@forEach
            for (i in 0 until arr.length()) {
                val e = arr.optJSONArray(i) ?: continue
                out.putIfAbsent(e.optString(0), Info(e.optString(3), e.optString(4), label))
            }
        }
        return out
    }
}
