package com.shelf.launcher

import com.shelf.launcher.data.CategorySettings

/**
 * 682ay (Arcade B, experimental): how the game you were on is saved for the next start. [legacy] = the old way (the key written
 * into the UI state, which rebuilt the whole home ~400 ms after every game change); else only the saved setting is written.
 * [legacy] is changed only by the isolated QA app's `voltcap_test_focussave` (same-build A/B).
 */
internal object FocusSave {
    @Volatile var legacy = false

    /** A category save carries the newest saved focus key, so it never writes an older one back. Pure. */
    fun merge(s: CategorySettings, savedKey: String?): CategorySettings =
        if (savedKey == null || savedKey == s.lastFocusedKey) s else s.copy(lastFocusedKey = savedKey)
}
