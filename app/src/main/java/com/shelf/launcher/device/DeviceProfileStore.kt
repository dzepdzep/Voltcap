package com.shelf.launcher.device

import android.content.Context

/**
 * Per-device profile choices. Classified DEVICE in SettingsScopes. The stored
 * device key guards against prefs restored onto another handheld: an override
 * made for a different device is dropped and detection runs afresh.
 */
class DeviceProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_profile", Context.MODE_PRIVATE)
    private val key get() = BuildFingerprint.current().deviceKey

    private fun sameDevice(): Boolean = prefs.getString(KEY_DEVICE, null) == key

    /** Clears everything saved for another device. */
    private fun ensureOwn() {
        if (!sameDevice()) prefs.edit().clear().putString(KEY_DEVICE, key).apply()
    }

    /** The forced adapter id, or null for Automatic (also when the prefs came from another device). */
    fun override(): String? {
        ensureOwn()
        return prefs.getString(KEY_OVERRIDE, null)
    }

    fun setOverride(adapterId: String?) {
        ensureOwn()
        prefs.edit().apply { if (adapterId == null) remove(KEY_OVERRIDE) else putString(KEY_OVERRIDE, adapterId) }.apply()
    }

    fun recordDetected(adapterId: String) {
        ensureOwn()
        if (prefs.getString(KEY_DETECTED, null) != adapterId) prefs.edit().putString(KEY_DETECTED, adapterId).apply()
    }

    fun detectedId(): String? = if (sameDevice()) prefs.getString(KEY_DETECTED, null) else null

    private companion object {
        const val KEY_DEVICE = "device_key"
        const val KEY_OVERRIDE = "override_adapter"
        const val KEY_DETECTED = "detected_adapter"
    }
}
