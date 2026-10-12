package com.shelf.launcher.data

import android.content.Context

/**
 * Opens the encrypted credential stores on a background thread the moment Voltcap starts, before the
 * home screen's first frame asks for them. Each one is checked value by value against Android's
 * Keystore, which right after a reboot can take a long while; done on the main thread it held up
 * the start-up animation. [SecureStore] caches what's opened, so the screen then finds them ready
 * (or waits only for what's left).
 */
object StartupPrewarm {
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread({
            listOf<(Context) -> Any>(
                ::SgdbKeyStore, ::RaCredentialsStore, ::MetadataSourceStore, ::TgdbKeyStore,
                ::IgdbCredentialsStore, ::ScreenScraperStore, ::RawgKeyStore
            ).forEach { make -> runCatching { make(app) } }
        }, "voltcap-prewarm").start()
    }
}
