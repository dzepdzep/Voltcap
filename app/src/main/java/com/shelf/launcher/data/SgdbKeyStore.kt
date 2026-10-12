package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the SteamGridDB API key so it survives app restarts, encrypted at
 * rest via Jetpack Security (a hardware-backed key in the Android Keystore
 * wraps the actual encryption key — the key itself never touches disk).
 *
 * Falls back to a plain SharedPreferences file if the Keystore is
 * unavailable for some reason (rare, but seen on a handful of OEM ROMs) —
 * better to still save the key than to crash the app over it. Also migrates
 * a key saved by an earlier plaintext version of this store, so upgrading
 * doesn't silently drop it.
 */
class SgdbKeyStore(context: Context) {
    private val app = context.applicationContext
    // The old plain-text file (sgdb_settings) is migrated into the encrypted store by SecureStore.
    private val opened = SecureStore.open(app, ENCRYPTED_PREFS_NAME, "SteamGridDB", listOf(LEGACY_PREFS_NAME))
    private val prefs: SharedPreferences = opened.prefs

    fun loadKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
        SecureStore.clearRelogin(app, "SteamGridDB")
    }

    fun clearKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    /** True once the key is actually sitting in the encrypted store — lets
     * the UI be honest if it ever had to fall back to plaintext. */
    fun isEncrypted(): Boolean = opened.encrypted



    companion object {
        private const val LEGACY_PREFS_NAME = "sgdb_settings"
        private const val ENCRYPTED_PREFS_NAME = "sgdb_settings_encrypted"
        private const val KEY_API_KEY = "api_key"
    }
}
