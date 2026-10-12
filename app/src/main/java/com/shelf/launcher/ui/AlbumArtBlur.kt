package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One album-art blur for every music player (Settings › Music & Audio › "Blurred album art" and its
 * strength): the corner mini player, the R1 player, the full player, the second screen's Music page,
 * the music overlay over games and the screensaver's music scene. 0–100 %; each player maps it onto
 * the blur that suits its size ([radius]). Off = no album-art backdrop anywhere.
 *
 * Kept in sync with the appearance settings by the home screen ([sync]); services and the companion
 * screen read it straight from the stored settings ([load]).
 */
object AlbumArtBlur {
    var enabled by mutableStateOf(true)
        private set
    var percent by mutableIntStateOf(3)
        private set

    fun sync(enabled: Boolean, percent: Int) {
        if (this.enabled != enabled) this.enabled = enabled
        val p = percent.coerceIn(0, 100)
        if (this.percent != p) this.percent = p
    }

    /** From the stored settings (for the overlay service and the companion screen). */
    fun load(context: Context) {
        runCatching {
            val a = com.shelf.launcher.data.AppearanceStore(context).load()
            sync(a.secondMusicBlurArt, a.secondMusicBlur)
        }
    }

    /** The blur for a player whose strongest setting is [maxDp] (small players use less). */
    fun radius(maxDp: Float): Dp = if (enabled) (percent / 100f * maxDp).dp else 0.dp

    /**
     * A blurred copy of [art] for plain Android views (the overlay over games), where Compose's blur
     * isn't available: scaled down and back up, which softens like a blur and costs almost nothing.
     * Null when the blur is off or there's no art.
     */
    fun softened(art: Bitmap?): Bitmap? {
        if (art == null || !enabled) return null
        return runCatching {
            // 0 % = barely softened (1/2 size), 100 % = very soft (1/24 size).
            val factor = 2 + (percent / 100f * 22f).toInt()
            val w = (art.width / factor).coerceAtLeast(2)
            val h = (art.height / factor).coerceAtLeast(2)
            val small = Bitmap.createScaledBitmap(art, w, h, true)
            Bitmap.createScaledBitmap(small, (w * 8).coerceAtMost(256), (h * 8).coerceAtMost(256), true)
        }.getOrNull()
    }
}
