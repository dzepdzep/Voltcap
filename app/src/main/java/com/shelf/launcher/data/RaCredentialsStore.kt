package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences

data class RaCredentials(val username: String, val apiKey: String)

/**
 * Persists RetroAchievements credentials (username + web API key) encrypted
 * at rest via Jetpack Security — same approach and same reasoning as
 * SgdbKeyStore.kt. Opened through SecureStore (never plain text); isEncrypted() reports
 * honestly whether the Keystore was usable.
 *
 * Two accounts can be saved (slot 0 is the original, slot 1 the second one). [load] / [save] / [clear] work on the
 * ACTIVE one, so everything that reads the RetroAchievements login follows the player who is on
 * ([setActive], the "Who is playing" screen). Which slot is active is a plain preference ("ra_account_slot").
 */
class RaCredentialsStore(context: Context) {
    private val app = context.applicationContext
    private val openedA = SecureStore.open(app, ENCRYPTED_PREFS_NAME, SERVICE, listOf(FALLBACK_PREFS_NAME))
    private val openedB by lazy { SecureStore.open(app, ENCRYPTED_PREFS_NAME_B, SERVICE, listOf(FALLBACK_PREFS_NAME_B)) }
    private val slotPrefs = app.getSharedPreferences(SLOT_PREFS, Context.MODE_PRIVATE)

    private fun prefsOf(slot: Int): SharedPreferences = if (slot == 1) openedB.prefs else openedA.prefs

    fun isEncrypted(): Boolean = openedA.encrypted

    /** The account in use: 0 or 1 (an empty second slot counts as 0). */
    fun activeSlot(): Int = slotPrefs.getInt(KEY_SLOT, 0).coerceIn(0, 1).let { if (it == 1 && loadSlot(1) == null) 0 else it }

    fun setActive(slot: Int) { slotPrefs.edit().putInt(KEY_SLOT, slot.coerceIn(0, 1)).commit() }

    /**
     * (Update 318, Flip 2 freeze — crash report 2026-10-22 19:36: opening a game's window read the login on the main
     * thread, and its first decryption through the Keystore took over 5 s.) Each slot is decrypted once and kept in memory
     * (shared by every instance; a save / clear forgets it) — [warm] does the first read off the main thread at start.
     */
    fun loadSlot(slot: Int): RaCredentials? {
        val s = slot.coerceIn(0, 1)
        synchronized(cacheLock) { if (cached[s]) return values[s] }
        val p = prefsOf(s)
        val username = p.getString(KEY_USERNAME, null)
        val apiKey = p.getString(KEY_API_KEY, null)
        val v = if (username != null && apiKey != null) RaCredentials(username, apiKey) else null
        synchronized(cacheLock) { values[s] = v; cached[s] = true }
        return v
    }

    /** Update 318: decrypts both accounts now (call off the main thread), so later reads are instant. */
    fun warm() { runCatching { loadSlot(0); loadSlot(1) } }

    fun saveSlot(slot: Int, credentials: RaCredentials) {
        prefsOf(slot.coerceIn(0, 1)).edit()
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_API_KEY, credentials.apiKey)
            .apply()
        forget()
        SecureStore.clearRelogin(app, SERVICE)
    }

    fun clearSlot(slot: Int) {
        prefsOf(slot.coerceIn(0, 1)).edit().remove(KEY_USERNAME).remove(KEY_API_KEY).apply()
        forget()
        if (slot == 1 && activeSlot() == 1) setActive(0)
    }

    /** Both accounts' user names (null: empty slot). */
    fun usernames(): List<String?> = listOf(loadSlot(0)?.username, loadSlot(1)?.username)

    /** The active account. */
    fun load(): RaCredentials? = loadSlot(activeSlot())

    /** Saves into the active account. */
    fun save(credentials: RaCredentials) = saveSlot(activeSlot(), credentials)

    /** Forgets the account in [slot]. The other one stays and is the one in use (the second moves up into slot 0). */
    fun remove(slot: Int) {
        val second = loadSlot(1)
        if (slot == 0 && second != null) { saveSlot(0, second); clearSlot(1) } else clearSlot(slot)
        setActive(0)
    }

    /** Forgets the active account; the other one, if any, is the one in use afterwards. */
    fun clear() = remove(activeSlot())

    companion object {
        private val cacheLock = Any()
        private val cached = BooleanArray(2)
        private val values = arrayOfNulls<RaCredentials>(2)
        /** Update 318: the next read decrypts again (after a save or a clear). */
        fun forget() = synchronized(cacheLock) { cached.fill(false); values.fill(null) }
        private const val SERVICE = "RetroAchievements"
        private const val ENCRYPTED_PREFS_NAME = "ra_credentials_encrypted"
        private const val FALLBACK_PREFS_NAME = "ra_credentials"
        private const val ENCRYPTED_PREFS_NAME_B = "ra_credentials_b_encrypted"
        private const val FALLBACK_PREFS_NAME_B = "ra_credentials_b"
        private const val SLOT_PREFS = "ra_account_slot"
        private const val KEY_SLOT = "slot"
        private const val KEY_USERNAME = "username"
        private const val KEY_API_KEY = "api_key"
    }
}
