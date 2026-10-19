package com.shelf.launcher.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/**
 * The usable part of the screen, shared by everything that animates, floats or can be moved:
 * achievement pop-ups and their jumping badges, lyrics, badges, game names, categories, the music
 * player, overlays and Edit Home widgets. Instead of every screen fixing its own clipping, they all
 * keep inside this zone.
 *
 * The zone is the screen minus system bars / display cutouts (when shown) minus a small responsive
 * margin (bigger on large screens, smaller on tiny handheld and second screens), plus the optional
 * extra margin from Settings (Features.safeZoneExtra, for TVs with overscan).
 *
 * [width]/[height] are the whole screen as the app lays it out (the size setting included);
 * [left]/[top]/[right]/[bottom] are how far the usable area stays from each edge.
 */
@Immutable
data class SafeZone(
    val width: Dp,
    val height: Dp,
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp
) {
    val usableWidth: Dp get() = (width - left - right).coerceAtLeast(0.dp)
    val usableHeight: Dp get() = (height - top - bottom).coerceAtLeast(0.dp)

    /** A small handheld or second screen (short side under ~420 dp): use compact layouts. */
    val compact: Boolean get() = minOf(width, height) < 420.dp

    /** A very wide screen (21:9 and wider). */
    val ultraWide: Boolean get() = height > 0.dp && width / height > 2.1f

    fun padding(): PaddingValues = PaddingValues(start = left, top = top, end = right, bottom = bottom)

    /**
     * Where a box of [w]×[h] whose top-left wants to be at [x],[y] (screen dp) may actually go so it
     * stays completely inside the zone. A box bigger than the zone is pinned to its top-left.
     */
    fun clamp(x: Dp, y: Dp, w: Dp, h: Dp): DpOffset = DpOffset(
        x.coerceIn(left, (width - right - w).coerceAtLeast(left)),
        y.coerceIn(top, (height - bottom - h).coerceAtLeast(top))
    )

    /** The top-left of a [w]×[h] box placed at fractions [fx],[fy] (0..1) of the free room inside the zone. */
    fun place(fx: Float, fy: Float, w: Dp, h: Dp): DpOffset = DpOffset(
        left + (usableWidth - w).coerceAtLeast(0.dp) * fx.coerceIn(0f, 1f),
        top + (usableHeight - h).coerceAtLeast(0.dp) * fy.coerceIn(0f, 1f)
    )

    /** The inverse of [place]: which fractions a [w]×[h] box at [x],[y] sits at. */
    fun fractionOf(x: Dp, y: Dp, w: Dp, h: Dp): Pair<Float, Float> {
        val fw = (usableWidth - w).coerceAtLeast(1.dp)
        val fh = (usableHeight - h).coerceAtLeast(1.dp)
        return ((x - left) / fw).coerceIn(0f, 1f) to ((y - top) / fh).coerceIn(0f, 1f)
    }

    /** How far something at screen y [y] may move up / down before it would leave the zone. */
    fun roomAbove(y: Dp): Dp = (y - top).coerceAtLeast(0.dp)
    fun roomBelow(y: Dp): Dp = (height - bottom - y).coerceAtLeast(0.dp)

    companion object {
        /** The responsive margin for a screen with short side [shortSide]: 8 dp on tiny screens, up to ~20 dp on big ones. */
        fun marginFor(shortSide: Dp, extraPercent: Int): Dp =
            (shortSide * 0.025f).coerceIn(8.dp, 20.dp) + shortSide * (extraPercent.coerceIn(0, 8) / 100f)
    }
}

/** Provided once at the root of each screen (home, second screen); null outside of one. */
val LocalSafeZone = compositionLocalOf<SafeZone?> { null }

/** The safe zone of the screen this composable is on. */
@Composable
fun safeZone(): SafeZone = LocalSafeZone.current ?: computeSafeZone()

@Composable
private fun computeSafeZone(): SafeZone {
    val size = screenSizeDp()
    // (Update 260, RP Classic: the system bars and the cut-out only — not the on-screen keyboard. safeDrawing counts the
    // keyboard too, so while it was up for a search (and on the RP Classic for a while after it went) the zone lost
    // 213 dp at the bottom and every part placed by fractions jumped up: the achievements card you put under the games
    // sat over them.)
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val dir = LocalLayoutDirection.current
    val extra = Features.safeZoneExtra.state.value
    return remember(size, insets, dir, extra) {
        val m = SafeZone.marginFor(minOf(size.width, size.height), extra)
        SafeZone(
            width = size.width,
            height = size.height,
            left = maxOf(insets.calculateLeftPadding(dir), 0.dp) + m,
            top = insets.calculateTopPadding() + m,
            right = maxOf(insets.calculateRightPadding(dir), 0.dp) + m,
            bottom = insets.calculateBottomPadding() + m
        )
    }
}

/** Computes the zone for this screen and hands it to everything inside [content]. */
@Composable
fun ProvideSafeZone(content: @Composable () -> Unit) {
    val zone = computeSafeZone()
    CompositionLocalProvider(LocalSafeZone provides zone, content = content)
}

/** Pads a full-screen layer so its children stay inside the safe zone. */
fun Modifier.safeZonePadding(): Modifier = composed { padding(safeZone().padding()) }

/**
 * For views drawn outside Compose (the in-game overlays): the usable rectangle of the display in
 * pixels, with the same responsive margin.
 */
object SafeZones {
    fun displayRect(context: Context): android.graphics.Rect {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val dm = context.resources.displayMetrics
        val rect: android.graphics.Rect
        var insetL = 0; var insetT = 0; var insetR = 0; var insetB = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            rect = android.graphics.Rect(metrics.bounds)
            val ins = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.displayCutout()
            )
            insetL = ins.left; insetT = ins.top; insetR = ins.right; insetB = ins.bottom
            rect.offsetTo(0, 0)
        } else {
            rect = android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val shortDp = minOf(rect.width(), rect.height()) / dm.density
        val marginPx = (SafeZone.marginFor(shortDp.dp, Features.safeZoneExtra.value).value * dm.density).toInt()
        return android.graphics.Rect(
            rect.left + insetL + marginPx,
            rect.top + insetT + marginPx,
            rect.right - insetR - marginPx,
            rect.bottom - insetB - marginPx
        )
    }

    /** Clamps an overlay of [w]×[h] px at [x],[y] into [zone]. */
    fun clamp(zone: android.graphics.Rect, x: Int, y: Int, w: Int, h: Int): android.graphics.Point =
        android.graphics.Point(
            x.coerceIn(zone.left, maxOf(zone.left, zone.right - w)),
            y.coerceIn(zone.top, maxOf(zone.top, zone.bottom - h))
        )
}

/**
 * Overlays drawn over other apps register the screen rectangles they occupy here, so the next one
 * (achievement overlay, music player, performance readout) can pick a spot that doesn't overlap.
 * Only what is actually visible is registered: a hidden music player frees its corner.
 */
object OverlayRegistry {
    private val taken = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Rect>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun set(id: String, rect: android.graphics.Rect?) {
        val old = taken[id]
        if (rect == null || rect.isEmpty) { if (taken.remove(id) != null) notifyChanged() }
        else if (old != rect) { taken[id] = android.graphics.Rect(rect); notifyChanged() }
    }

    fun others(id: String): List<android.graphics.Rect> = taken.filterKeys { it != id }.values.map { android.graphics.Rect(it) }

    fun isTaken(id: String): Boolean = taken.containsKey(id)

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyChanged() { listeners.forEach { runCatching { it() } } }

    /**
     * Moves a [w]×[h] box that wants to sit at [x],[y] so it overlaps none of the other overlays:
     * first down / up along its side, then to the other side; stays put when nothing fits.
     */
    fun avoid(id: String, zone: android.graphics.Rect, x: Int, y: Int, w: Int, h: Int, gap: Int): android.graphics.Point {
        val others = others(id)
        fun free(px: Int, py: Int): Boolean {
            val r = android.graphics.Rect(px, py, px + w, py + h)
            return zone.contains(r) && others.none { android.graphics.Rect.intersects(it, r) }
        }
        val start = SafeZones.clamp(zone, x, y, w, h)
        if (free(start.x, start.y)) return start
        // Slide along the same side: below / above each blocker.
        val candidates = mutableListOf<Int>()
        others.forEach { o -> candidates += o.bottom + gap; candidates += o.top - gap - h }
        candidates.sortedBy { kotlin.math.abs(it - start.y) }.forEach { cy ->
            if (free(start.x, cy)) return android.graphics.Point(start.x, cy)
        }
        // The other side of the screen.
        val otherX = if (start.x + w / 2 < (zone.left + zone.right) / 2) zone.right - w else zone.left
        if (free(otherX, start.y)) return android.graphics.Point(otherX, start.y)
        candidates.sortedBy { kotlin.math.abs(it - start.y) }.forEach { cy ->
            if (free(otherX, cy)) return android.graphics.Point(otherX, cy)
        }
        return start
    }
}
