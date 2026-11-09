package com.shelf.launcher.ui

import com.shelf.launcher.data.Features

/**
 * Update 480 (B4, master request: a shared animation system — Off / Subtle / Normal / Expressive, reduced motion).
 *
 * One clock speed for every Compose animation in Voltcap's windows: popups, Settings, R1, the category screen, the drawer,
 * the profile picker, Details, More / Less and the dialogs all run their OWN animations (each layout keeps its own design),
 * only faster, slower or not at all. It works as the [androidx.compose.ui.MotionDurationScale] of the root recomposer
 * (setKeyRootContent), which the dialogs and popups share as child compositions.
 *  - Off: transitions finish at once (0). Normal: as designed (1). Subtle: quicker (0.6). Expressive: more time (1.3).
 *  - Android's "Remove animations" (animator duration scale 0) always wins: reduced motion means 0, whatever the level;
 *    other system scales multiply.
 *  - Sliding names (basicMarquee) keep their own fixed speed (Compose runs them outside this scale), so a long name still
 *    slides to show the rest with motion Off. The layouts' ambient scenes run on their own clocks and are not touched.
 */
internal object MotionLevel : androidx.compose.ui.MotionDurationScale {
    @Volatile private var system = 1f

    fun factorFor(level: Features.Motion): Float = when (level) {
        Features.Motion.OFF -> 0f
        Features.Motion.SUBTLE -> 0.6f
        Features.Motion.NORMAL -> 1f
        Features.Motion.EXPRESSIVE -> 1.3f
    }

    override val scaleFactor: Float
        get() = factorFor(Features.motion.value) * system

    /** Reads Android's animator duration scale again (at start and whenever the app comes back). */
    fun refresh(context: android.content.Context) {
        system = runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f).coerceIn(0f, 10f)
    }
}
