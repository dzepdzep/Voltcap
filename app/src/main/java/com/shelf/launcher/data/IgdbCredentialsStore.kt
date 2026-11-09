package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences

data class IgdbCredentials(val clientId: String, val clientSecret: String)

/**
 * Update 516 (owner): IGDB access needs a Twitch developer app — not something a regular user has. Voltcap ships its own
 * (build time, from the gitignored igdb.properties; never in git, the patch or the docs). Turning IGDB on then needs no key;
 * builds without it keep the Client ID / Secret fields. A key someone saved themselves still wins.
 */
object IgdbBuiltInKey {
    val builtIn: IgdbCredentials? get() = of(com.shelf.launcher.BuildConfig.IGDB_CLIENT_ID, com.shelf.launcher.BuildConfig.IGDB_CLIENT_SECRET)

    internal fun of(id: String?, secret: String?): IgdbCredentials? {
        val i = id?.trim().orEmpty(); val s = secret?.trim().orEmpty()
        return if (i.isEmpty() || s.isEmpty()) null else IgdbCredentials(i, s)
    }

    internal fun resolve(savedId: String?, savedSecret: String?, builtIn: IgdbCredentials?): IgdbCredentials? =
        of(savedId, savedSecret) ?: builtIn
}

/** Same encrypted-at-rest approach as the SteamGridDB and RetroAchievements
 * stores, with the same honest fallback if the Keystore is unavailable. */
class IgdbCredentialsStore(context: Context) {
    private val app = context.applicationContext
    private val opened = SecureStore.open(app, ENCRYPTED, "IGDB", listOf(FALLBACK))
    private val prefs: SharedPreferences = opened.prefs

    fun isEncrypted(): Boolean = opened.encrypted

    fun load(): IgdbCredentials? {
        // (Turned on = something was saved. Update 516: a blank key there means the one built into the app.)
        val id = prefs.getString(KEY_ID, null) ?: return null
        return IgdbBuiltInKey.resolve(id, prefs.getString(KEY_SECRET, null), IgdbBuiltInKey.builtIn)
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
