package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shelf.launcher.ui.theme.AppTheme

/** A user-saved preset, stored as names so renamed or removed enum values are simply skipped. */
data class CustomPresetData(
    val id: String = "",
    val name: String = "",
    val theme: String? = null,
    val values: Map<String, String> = emptyMap(),
    /** Update 125: every look setting ("appearance" prefs, typed: b:/i:/l:/f:/s:) and every feature setting, so the
     *  preset carries everything on the home screen (Edit Home, parts, layout options, what shows where). */
    val appearanceAll: Map<String, String>? = null,
    val features: Map<String, String>? = null
)

/** What was active / in place at one moment: the preset-controlled values plus the theme. */
data class PresetSnapshot(
    val presetId: String? = null,
    val values: Map<String, String> = emptyMap(),
    val theme: String? = null,
    /** Update 125: the feature settings at that moment (so Undo after one of your own presets puts them back too). */
    val features: Map<String, String>? = null,
    /** Update 144: every look setting at that moment (typed, like [CustomPresetData.appearanceAll]), kept when one of
     *  your own presets rewrote the whole home screen, so Undo puts all of it back, not only the preset fields. */
    val appearanceAll: Map<String, String>? = null
)

/**
 * Presets state in prefs "layout_presets" (GLOBAL scope, so it is backed up):
 * the active preset with a snapshot of its values (to spot "(modified)"), one
 * undo step and up to [MAX_CUSTOM] custom presets.
 *
 * The undo step keeps the preset-controlled fields and the feature settings; after
 * one of your own presets (which rewrites every look setting) it keeps every look
 * setting too (Update 144).
 */
/** The values [this] preset sets, read from [s] (only its own fields, so changing anything else isn't "(modified)"). */
private fun LayoutPreset.recorded(s: AppearanceSettings): Map<String, String> =
    PresetFields.read(s).let { all -> keys?.let { k -> all.filterKeys { it in k } } ?: all }

class LayoutPresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("layout_presets", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val app = context.applicationContext

    /** Settings a preset never carries: device and account things that aren't the home screen's look. */
    private fun notLook(key: String): Boolean = key.startsWith("lights") || key.startsWith("discord") ||
        key.contains("token", ignoreCase = true) || key.contains("api_key", ignoreCase = true) || key.contains("account", ignoreCase = true) ||
        key.startsWith("battery_watch") || key.startsWith("who_plays")

    /** Every look setting, typed, as it is saved now. */
    private fun appearanceNow(cur: AppearanceSettings): Map<String, String> {
        AppearanceStore(app).save(cur)
        return app.getSharedPreferences("appearance", Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            if (notLook(k)) return@mapNotNull null
            when (v) {
                is Boolean -> k to "b:$v"
                is Int -> k to "i:$v"
                is Long -> k to "l:$v"
                is Float -> k to "f:$v"
                is String -> k to "s:$v"
                is Set<*> -> k to "S:" + gson.toJson(v.map { it.toString() })
                else -> null
            }
        }.toMap()
    }

    /** Puts back a saved preset's every setting; returns the settings now in place (null when it has none saved). */
    private fun restoreAll(d: CustomPresetData): AppearanceSettings? {
        val all = d.appearanceAll ?: return null
        writeAppearance(all, clearOthers = false)
        d.features?.let { f -> Features.restore(f.filterKeys { !notLook(it) }) }
        return AppearanceStore(app).load()
    }

    /** Writes typed look settings ([appearanceNow]'s form) into the "appearance" prefs; [clearOthers] also takes away
     *  the look settings that aren't in [all] (Undo: back to exactly what was there). */
    private fun writeAppearance(all: Map<String, String>, clearOthers: Boolean) {
        val p = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val e = p.edit()
        if (clearOthers) p.all.keys.forEach { k -> if (!notLook(k) && k !in all) e.remove(k) }
        all.forEach { (k, v) ->
            if (notLook(k) || v.length < 2) return@forEach
            val body = v.substring(2)
            when (v.take(2)) {
                "b:" -> e.putBoolean(k, body == "true")
                "i:" -> body.toIntOrNull()?.let { e.putInt(k, it) }
                "l:" -> body.toLongOrNull()?.let { e.putLong(k, it) }
                "f:" -> body.toFloatOrNull()?.let { e.putFloat(k, it) }
                "s:" -> e.putString(k, body)
                "S:" -> runCatching { gson.fromJson<List<String>>(body, object : TypeToken<List<String>>() {}.type) }.getOrNull()?.let { e.putStringSet(k, it.toSet()) }
            }
        }
        e.commit()
    }

    fun customs(): List<CustomPresetData> = runCatching {
        gson.fromJson<List<CustomPresetData?>>(prefs.getString("custom", null) ?: "[]",
            object : TypeToken<List<CustomPresetData?>>() {}.type)?.filterNotNull()?.filter { it.id.isNotBlank() }
    }.getOrNull() ?: emptyList()

    private fun saveCustoms(list: List<CustomPresetData>) = prefs.edit().putString("custom", gson.toJson(list)).apply()

    fun all(): List<LayoutPreset> = LayoutPresets.builtIns + customs().map { it.toPreset() }

    fun find(id: String?): LayoutPreset? = id?.let { i -> all().firstOrNull { it.id == i } }

    fun activeId(): String? = prefs.getString("active_id", null)

    private fun activeSnapshot(): PresetSnapshot? = runCatching {
        gson.fromJson(prefs.getString("active_snapshot", null), PresetSnapshot::class.java)
    }.getOrNull()

    fun hasUndo(): Boolean = prefs.contains("undo")

    private fun undoSnapshot(): PresetSnapshot? = runCatching {
        gson.fromJson(prefs.getString("undo", null), PresetSnapshot::class.java)
    }.getOrNull()

    /** True when the active preset's controlled fields (or theme) no longer match what it set. */
    fun isModified(cur: AppearanceSettings, theme: AppTheme): Boolean {
        val snap = activeSnapshot() ?: return false
        // Only what the preset recorded counts (a field added later, like the home layout, isn't "a change").
        val now = PresetFields.read(cur)
        if (snap.values.any { (k, v) -> now[k] != v }) return true
        return snap.theme != null && snap.theme != theme.name
    }

    /** Applies [preset]; returns the new settings and the theme to select (null = unchanged). */
    fun apply(
        preset: LayoutPreset, cur: AppearanceSettings, theme: AppTheme,
        /** The settings Undo goes back to, when the apply came in two steps (layout switched first). Default: [cur]. */
        before: AppearanceSettings = cur
    ): Pair<AppearanceSettings, AppTheme?> {
        // Update 144: the undo point is taken before anything changes (restoreAll below already rewrites the feature
        // settings), and with every look setting when the preset rewrites them all. appearanceNow saves [before]
        // first, which restoreAll then writes over.
        val featBefore = Features.snapshot { notLook(it) }
        val allBefore = if (preset.restoreAll != null) appearanceNow(before) else null
        // Update 125: your own presets put back everything they saved (the whole home screen).
        val result = preset.restoreAll?.invoke() ?: preset.apply(cur)
        val newTheme = preset.theme
        // Reset to preset must not overwrite the undo point with an already-applied state? It should
        // still be undoable, so every apply records the state right before it.
        prefs.edit()
            .putString("undo", gson.toJson(PresetSnapshot(activeId(), PresetFields.read(before), theme.name, featBefore, allBefore)))
            .putString("active_id", preset.id)
            .putString("active_snapshot", gson.toJson(PresetSnapshot(preset.id, preset.recorded(result), newTheme?.name)))
            .apply()
        return result to newTheme
    }

    /** Restores the state from before the last apply; null when there is nothing to undo. */
    fun undo(cur: AppearanceSettings): Pair<AppearanceSettings, AppTheme?>? {
        val snap = undoSnapshot() ?: return null
        // Update 144: after one of your own presets, every look setting goes back as it was.
        val all = snap.appearanceAll
        val restored = if (all != null) {
            writeAppearance(all, clearOthers = true)
            AppearanceStore(app).load()
        } else PresetFields.write(cur, snap.values)
        snap.features?.let { Features.restore(it) }
        val theme = AppTheme.values().firstOrNull { it.name == snap.theme }
        val e = prefs.edit().remove("undo")
        val prev = snap.presetId
        if (prev != null && find(prev) != null) {
            e.putString("active_id", prev)
            // The earlier preset's own values are what "modified" is measured against again.
            find(prev)?.let { p ->
                e.putString("active_snapshot", gson.toJson(PresetSnapshot(prev, p.recorded(p.apply(restored)), p.theme?.name)))
            }
        } else {
            e.remove("active_id").remove("active_snapshot")
        }
        e.apply()
        return restored to theme
    }

    /** Saves the current look as a new preset; null when the limit of [MAX_CUSTOM] is reached. */
    fun saveCurrent(name: String, cur: AppearanceSettings, theme: AppTheme): CustomPresetData? {
        val list = customs()
        if (list.size >= MAX_CUSTOM) return null
        // Update 125: with every look and feature setting, so it carries the whole home screen.
        val data = CustomPresetData("c_" + System.currentTimeMillis(), name.trim().ifBlank { "My look" }.take(40),
            theme.name, PresetFields.read(cur), appearanceNow(cur), Features.snapshot { notLook(it) })
        saveCustoms(list + data)
        prefs.edit().putString("active_id", data.id)
            .putString("active_snapshot", gson.toJson(PresetSnapshot(data.id, data.values, data.theme))).apply()
        return data
    }

    /** A preset as a short text code to send to someone (share sheet, chat, QR…). */
    fun exportCode(id: String): String? {
        val c = customs().firstOrNull { it.id == id } ?: return null
        val json = gson.toJson(c.copy(id = ""))
        return CODE_PREFIX + android.util.Base64.encodeToString(json.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    /** Adds a preset from someone's code; null when the code isn't valid or the limit is reached. */
    fun importCode(code: String): CustomPresetData? {
        val body = code.trim().substringAfter(CODE_PREFIX, "").trim()
        if (body.isEmpty()) return null
        val data = runCatching {
            val json = String(android.util.Base64.decode(body, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE), Charsets.UTF_8)
            gson.fromJson(json, CustomPresetData::class.java)
        }.getOrNull() ?: return null
        @Suppress("SENSELESS_COMPARISON")
        if (data.values == null || data.values.isEmpty()) return null
        val list = customs()
        if (list.size >= MAX_CUSTOM) return null
        // Only keys Voltcap knows are kept; values are applied through PresetFields like any preset.
        val clean = data.copy(id = "c_" + System.currentTimeMillis(), name = (data.name ?: "Shared look").take(40).ifBlank { "Shared look" })
        saveCustoms(list + clean)
        return clean
    }

    fun rename(id: String, name: String) {
        val n = name.trim().take(40)
        if (n.isBlank()) return
        saveCustoms(customs().map { if (it.id == id) it.copy(name = n) else it })
    }

    fun delete(id: String) {
        saveCustoms(customs().filterNot { it.id == id })
        if (activeId() == id) prefs.edit().remove("active_id").remove("active_snapshot").apply()
    }

    private fun CustomPresetData.toPreset() = LayoutPreset(
        id, name.ifBlank { "My look" }, "Your saved look.", false,
        AppTheme.values().firstOrNull { it.name == theme },
        { PresetFields.write(it, values) },
        restoreAll = if (appearanceAll != null) ({ restoreAll(this) }) else null
    )

    companion object {
        const val MAX_CUSTOM = 12
        const val CODE_PREFIX = "VOLTCAP-LOOK:"
    }
}
