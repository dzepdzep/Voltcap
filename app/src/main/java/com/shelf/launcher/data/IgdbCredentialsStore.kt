package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences

data class IgdbCredentials(val clientId: String, val clientSecret: String)

/** Same encrypted-at-rest approach as the SteamGridDB and RetroAchievements
 * stores, with the same honest fallback if the Keystore is unavailable. */
class IgdbCredentialsStore(context: Context) {
    private val app = context.applicationContext
    private val opened = SecureStore.open(app, ENCRYPTED, "IGDB", listOf(FALLBACK))
    private val prefs: SharedPreferences = opened.prefs

    fun isEncrypted(): Boolean = opened.encrypted

    fun load(): IgdbCredentials? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val secret = prefs.getString(KEY_SECRET, null) ?: return null
        return IgdbCredentials(id, secret)
    }

    fun save(creds: IgdbCredentials) {
        prefs.edit().putString(KEY_ID, creds.clientId).putString(KEY_SECRET, creds.clientSecret).apply()
        SecureStore.clearRelogin(app, "IGDB")
    }

    fun clear() {
        prefs.edit().remove(KEY_ID).remove(KEY_SECRET).apply()
    }


    companion object {
        private const val ENCRYPTED = "igdb_credentials_encrypted"
        private const val FALLBACK = "igdb_credentials"
        private const val KEY_ID = "client_id"
        private const val KEY_SECRET = "client_secret"
    }
}
