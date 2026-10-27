package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * The one place that opens an encrypted preferences file for a credential.
 *
 * Why it exists: Android's Auto Backup / device transfer used to copy the
 * `*_encrypted.xml` files (which hold their own Tink keyset) to a new device,
 * where the Keystore master key that wraps that keyset doesn't exist.
 * EncryptedSharedPreferences.create() then throws, and every store used to
 * swallow that and quietly switch to a plain-text file — the user looked
 * signed out, new secrets landed unencrypted, and the broken file was never
 * repaired. Now a broken file is deleted, a fresh encrypted one is created,
 * and a non-secret "needs sign-in again" flag is recorded for the UI.
 *
 * Secrets are never written in plain text. If the Keystore is truly unusable
 * the store is memory-only for this run (isEncrypted() is false).
 */
object SecureStore {
    private const val TAG = "SecureStore"
    private const val FLAGS = "secure_store_flags"
    private const val MASTER_ALIAS = "_androidx_security_master_key_"

    /** The prefs and whether they are really encrypted on disk. */
    class Opened(val prefs: SharedPreferences, val encrypted: Boolean)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Opened>()

    /**
     * Opens [encryptedName]. [service] is the friendly name shown in
     * "Sign in again to …". [plainNames] are older plain-text files whose
     * values move into the encrypted store once (then the plain file goes).
     */
    fun open(context: Context, encryptedName: String, service: String, plainNames: List<String> = emptyList()): Opened =
        // (QA pass 2, D24: an already open store is returned without the lock — the main thread used to wait up to
        // 0.3 s at start-up behind another thread creating a different store. Creating stays one at a time: they share
        // one master key, which must never be made twice.)
        cache[encryptedName] ?: run {
            // (QA: named in a system trace, so a wait for a store shows which one.)
            android.os.Trace.beginSection("VC:secureOpen:" + encryptedName.take(60))
            try { openLocked(context, encryptedName, service, plainNames) } finally { android.os.Trace.endSection() }
        }

    @Synchronized
    private fun openLocked(context: Context, encryptedName: String, service: String, plainNames: List<String>): Opened {
        cache[encryptedName]?.let { return it }
        val app = context.applicationContext
        val opened = create(app, encryptedName, service, plainNames)
        cache[encryptedName] = opened
        return opened
    }

    private fun create(app: Context, name: String, service: String, plainNames: List<String>): Opened {
        var repaired = false
        var prefs = tryOpen(app, name)
        if (prefs == null) {
            repaired = hadSecrets(app, name)
            Log.w(TAG, "Encrypted store $name is unreadable; recreating it")
            deleteFiles(app, name)
            prefs = tryOpen(app, name)
            if (prefs == null) {
                // The master key itself is the problem (invalidated / broken):
                // drop it and let every store re-key itself on its next open.
                runCatching {
                    java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_ALIAS)
                }
                deleteFiles(app, name)
                prefs = tryOpen(app, name)
            }
        }
        if (prefs == null) {
            if (repaired || plainNames.any { fileFor(app, it).exists() }) flag(app, service)
            return Opened(MemoryPrefs(readPlain(app, plainNames)), false)
        }
        migratePlain(app, prefs, plainNames)
        // A rebuilt store that was refilled from plain-text values needs no re-login.
        if (repaired) {
            if (prefs.all.isEmpty()) flag(app, service) else clearRelogin(app, service)
        }
        return Opened(prefs, true)
    }

    /** Opens and proves every value decrypts; null when anything is broken. */
    private fun tryOpen(app: Context, name: String): SharedPreferences? = try {
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            app, name, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.all // decrypts everything: a half-restored file throws here
        prefs
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't open encrypted store $name", e)
        null
    }

    /** True when the file holds stored values (not just the encryption keysets). */
    private fun hadSecrets(app: Context, name: String): Boolean = runCatching {
        Regex("name=\"([^\"]+)\"").findAll(fileFor(app, name).readText())
            .any { !it.groupValues[1].startsWith("__androidx_security_crypto_encrypted_prefs") }
    }.getOrDefault(false)

    private fun fileFor(app: Context, name: String) = File(File(app.applicationInfo.dataDir, "shared_prefs"), "$name.xml")

    /** Removes the encrypted file (its Tink keyset lives inside it) and any backup copy. */
    private fun deleteFiles(app: Context, name: String) {
        runCatching { app.deleteSharedPreferences(name) }
        runCatching { fileFor(app, name).delete() }
        runCatching { File(fileFor(app, name).path + ".bak").delete() }
    }

    private fun readPlain(app: Context, names: List<String>): Map<String, Any?> {
        val out = HashMap<String, Any?>()
        names.forEach { n ->
            if (fileFor(app, n).exists()) out.putAll(app.getSharedPreferences(n, Context.MODE_PRIVATE).all)
        }
        return out
    }

    /** Older plain-text copies move INTO the encrypted store, once. */
    private fun migratePlain(app: Context, target: SharedPreferences, names: List<String>) {
        names.forEach { n ->
            if (!fileFor(app, n).exists()) return@forEach
            val plain = app.getSharedPreferences(n, Context.MODE_PRIVATE)
            val edit = target.edit()
            plain.all.forEach { (k, v) ->
                if (target.contains(k)) return@forEach
                when (v) {
                    is String -> edit.putString(k, v)
                    is Boolean -> edit.putBoolean(k, v)
                    is Int -> edit.putInt(k, v)
                    is Long -> edit.putLong(k, v)
                    is Float -> edit.putFloat(k, v)
                    is Set<*> -> edit.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            // Only remove the plain copy once the encrypted one is on disk.
            if (edit.commit()) {
                plain.edit().clear().commit()
                runCatching { app.deleteSharedPreferences(n) }
            }
        }
    }

    // ---- "Sign in again" flags (non-secret) ----

    private fun flags(app: Context) = app.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)

    private fun flag(app: Context, service: String) {
        flags(app).edit().putBoolean("relogin_$service", true).apply()
    }

    /** Friendly names of services whose saved sign-in was lost and must be entered again. */
    fun pendingRelogin(context: Context): List<String> =
        flags(context.applicationContext).all.filter { it.key.startsWith("relogin_") && it.value == true }
            .map { it.key.removePrefix("relogin_") }.sorted()

    /** Marks [service] as needing a fresh sign-in on this device (e.g. after a restore). */
    fun flagRelogin(context: Context, service: String) = flag(context.applicationContext, service)

    /** Called when the user signs in again or dismisses the notice. */
    fun clearRelogin(context: Context, service: String) {
        val f = flags(context.applicationContext)
        if (f.contains("relogin_$service")) f.edit().remove("relogin_$service").apply()
    }

    /** In-memory stand-in used only when the Keystore can't be used at all: never touches disk. */
    private class MemoryPrefs(initial: Map<String, Any?>) : SharedPreferences {
        private val map = HashMap<String, Any?>(initial)

        @Synchronized override fun getAll(): MutableMap<String, *> = HashMap(map)
        @Synchronized override fun getString(key: String?, defValue: String?) = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        @Synchronized override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        @Synchronized override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
        @Synchronized override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
        @Synchronized override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
        @Synchronized override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
        @Synchronized override fun contains(key: String?) = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removed = HashSet<String>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { removed.add(key) }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                synchronized(this@MemoryPrefs) {
                    if (clear) map.clear()
                    removed.forEach { map.remove(it) }
                    pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
                }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
