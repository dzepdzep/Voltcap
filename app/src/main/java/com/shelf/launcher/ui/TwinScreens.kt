package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.HomeLayout

/*
 * Update 158: the two-screen layouts, Twin desk (TwinDesk.kt) and Twin icons (TwinIcons.kt). Each has a top part
 * (the main screen) and a bottom part (the second screen, e.g. the AYN Thor's lower screen). With a second screen up
 * and Settings › Screens › "Use the second screen" on, the main screen draws the top part and the second screen the
 * bottom part (LauncherScreen's SecondScreenContent asks [TwinSecondScreen] for it). Without one, the main screen
 * draws both parts, one above the other (or side by side when that fits much bigger), scaled to fit, so the layout
 * still works on a one-screen handheld. Both parts read the same StyleCtx: the D-pad, taps and the game you're on
 * are shared through the view model.
 */

/** Update 158: the twin layout's context as the main screen drew it last (null while no twin layout is on). */
internal object TwinScreens {
    var ctx by mutableStateOf<StyleCtx?>(null)
    /**
     * Update 180: the top part fills the whole main screen with its own background, the theme's background off (two
     * screens in use and Layout options › "Fill the top screen" on). Set by [TwinHost].
     */
    var fullTop by mutableStateOf(false)
    /**
     * Update 180: the second screen shows its usual dashboard (pages, dock, Games page…) instead of the layout's bottom
     * part — the Second screen button in the layout, or ↩ on the dashboard to come back.
     */
    var dashboard by mutableStateOf(false)
    /** Expands Current-only to the selectable category list until Done. */
    var categoriesExpanded by mutableStateOf(false)
}

/** Update 180: Layout options of both two-screen layouts: the top screen filled by the layout, the theme off. */
@Composable
internal fun TwinFullTopSwitch() {
    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
    SwitchRow(
        androidx.compose.material.icons.Icons.Filled.OpenInFull, "Fill the top screen",
        "With two screens: the layout's own background fills the whole top screen and the theme's background is off. One screen: as before.",
        com.shelf.launcher.data.Features.twinFullTop.state.value, { com.shelf.launcher.data.Features.twinFullTop.value = it }
    )
    TwinCommonControls()
}

/** Update 310: opt-in standard category picker and dashboard access for all five dual-screen layouts. */
@Composable
internal fun TwinCommonControls() {
    SwitchRow(
        androidx.compose.material.icons.Icons.Filled.Dashboard, "Open normal second screen",
        "Show a shortcut to Voltcap\'s normal second-screen pages and dock, with a button to return.",
        com.shelf.launcher.data.Features.twinDashboardAccess.state.value,
        { com.shelf.launcher.data.Features.twinDashboardAccess.value = it }
    )
    SwitchRow(
        androidx.compose.material.icons.Icons.Filled.OpenInFull, "Additional category bar",
        "Optional shared category picker on the lower screen. Follows Categories: All / Current only / Off, independently of each layout\'s own design.",
        com.shelf.launcher.data.Features.twinSharedCategories.state.value,
        { com.shelf.launcher.data.Features.twinSharedCategories.value = it }
    )
}

/**
 * Update 180: the button on the second screen's dashboard (opened from a two-screen layout) that brings the layout back.
 * Update 182: drawn in the layout's own style — Twin desk's and Twin icons' white bar with a round button (as their
 * Second screen button), the Pocket layouts' flat white panel with the pixel font and the corner brackets.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.TwinBackPill(name: String, layout: HomeLayout = HomeLayout.TWIN_DESK) {
    val back = { TwinScreens.dashboard = false }
    val lit = SecondPad.active && SecondPad.focused == "twin-back"
    val accent = ShelfTheme.accent
    val ink = Color(0xFF26313C)
    val edge = Color(0xFFC6CFD7)
    if (layout.isPocket) {
        val font = pocketFont()
        val pAccent = if (layout == HomeLayout.POCKET_RIBBON)
            pocketAccent(runCatching { PocketAccent.valueOf(com.shelf.launcher.data.Features.ribbonAccent.state.value) }.getOrDefault(PocketAccent.SKY))
        else pocketAccent(runCatching { PocketAccent.valueOf(com.shelf.launcher.data.Features.classicAccent.state.value) }.getOrDefault(PocketAccent.BLUE))
        androidx.compose.foundation.layout.Box(
            Modifier.align(Alignment.BottomStart).padding(10.dp)
                .padTargetPlain("twin-back") { back() }
                .tapOnly { back() }
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier.background(Color.White).border(1.dp, if (lit) Color(0xFF6B7380) else Color(0xFFB9BEC6))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(18.dp)) { pocketIcon(PocketIcons.BACK, Color(0xFF3A3F47), pAccent) }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                androidx.compose.material3.Text(name, color = Color(0xFF3A3F47), fontSize = 14.sp, fontFamily = font, maxLines = 1)
            }
            if (lit) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { pocketBrackets(2.dp.toPx(), pAccent) }
        }
    } else {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
        androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp)
                .padTargetPlain("twin-back") { back() }
                .clip(shape).background(Color.White.copy(alpha = 0.86f)).border(1.dp, edge, shape)
                .tapOnly { back() }
                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.graphicsLayer { val k = if (lit) 1.1f else 1f; scaleX = k; scaleY = k }
                    .size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (lit) accent.copy(alpha = 0.18f) else Color.White)
                    .border(if (lit) 2.dp else 1.dp, if (lit) accent else edge, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null,
                    tint = if (lit) ink else Color(0xFF4A5562), modifier = Modifier.size(19.dp)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            androidx.compose.material3.Text(name, color = ink, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Update 158: one of the two-screen layouts. */
internal val HomeLayout.isTwin: Boolean get() = this == HomeLayout.TWIN_DESK || this == HomeLayout.TWIN_ICONS ||
    this == HomeLayout.TWIN_SHOWCASE || // Update 307
    this == HomeLayout.POCKET_CLASSIC || this == HomeLayout.POCKET_RIBBON // Update 181

/** Update 181: the Pocket layouts (their own keys, sounds and pocket screens). */
internal val HomeLayout.isPocket: Boolean get() = this == HomeLayout.POCKET_CLASSIC || this == HomeLayout.POCKET_RIBBON

/** Update 158: the bottom part is on the second screen right now (Voltcap's own second screen is up and in use). */
internal fun twinOnSecond(state: LauncherUiState): Boolean =
    state.appearance.useSecondScreen && SecondScreenShown.value &&
        // Update 181: a Pocket layout set to show both screens on the main one (Layout options › Screens).
        !(state.appearance.homeLayout.isPocket && com.shelf.launcher.data.Features.pocketArrange.value != com.shelf.launcher.data.Features.PocketArrange.AUTO)

/**
 * Update 158: draws a twin layout on the main screen: only [top] while the second screen shows the bottom part,
 * else [top] and [bottom] together, scaled to fit. Each part gets its modifier and whether it is drawn as a small
 * framed screen (true) or fills a whole screen (false).
 */
@Composable
internal fun TwinHost(
    c: StyleCtx,
    modifier: Modifier,
    top: @Composable (Modifier, Boolean) -> Unit,
    bottom: @Composable (Modifier, Boolean) -> Unit,
    /** Update 181: the two parts' sizes when both are on one screen (scaled to fit), and the narrowest a part may be. */
    topH: Dp = 250.dp,
    bottomH: Dp = 300.dp,
    minW: Dp = 360.dp,
    /** Update 181: force one above the other (true) or side by side (false); null = whichever is bigger. */
    stacked: Boolean? = null
) {
    // The second screen draws the bottom part from this.
    // (Cleared on leaving only while it is still this layout's, so switching to the other twin layout keeps it.)
    val mine = remember { arrayOfNulls<StyleCtx>(1) }
    val fill = twinOnSecond(c.state) && com.shelf.launcher.data.Features.twinFullTop.state.value
    SideEffect { mine[0] = c; TwinScreens.ctx = c; if (TwinScreens.fullTop != fill) TwinScreens.fullTop = fill }
    DisposableEffect(Unit) { onDispose { if (TwinScreens.ctx === mine[0]) { TwinScreens.ctx = null; TwinScreens.fullTop = false } } }
    if (twinOnSecond(c.state)) {
        top(modifier.fillMaxSize(), false)
    } else {
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val gap = 10.dp
            // One above the other (the way the handheld holds its two screens), or side by side on a wide screen
            // when that is clearly bigger.
            val sStack = minOf(maxHeight / (topH + bottomH + gap), maxWidth / minW)
            val sSide = minOf(maxWidth / (minW * 2 + gap), maxHeight / (maxOf(topH, bottomH) - 20.dp))
            val side = stacked?.not() ?: (sSide > sStack * 1.15f)
            if (side) {
                val s = sSide.coerceAtLeast(0.3f)
                val h = minOf(maxHeight / s, maxOf(topH, bottomH) + 40.dp)
                val w = minOf((maxWidth / s - gap) / 2, 560.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap * s), verticalAlignment = Alignment.CenterVertically) {
                    TwinScaled(w, h, s) { m -> top(m, true) }
                    TwinScaled(w, h, s) { m -> bottom(m, true) }
                }
            } else {
                val s = sStack.coerceAtLeast(0.3f)
                val w = minOf(maxWidth / s, 560.dp)
                Column(verticalArrangement = Arrangement.spacedBy(gap * s), horizontalAlignment = Alignment.CenterHorizontally) {
                    TwinScaled(w, topH, s) { m -> top(m, true) }
                    TwinScaled(w, bottomH, s) { m -> bottom(m, true) }
                }
            }
        }
    }
}

/** Update 158: [content] laid out at [w] x [h] and drawn [s] times that size (taps follow the scale). */
@Composable
private fun TwinScaled(w: Dp, h: Dp, s: Float, content: @Composable (Modifier) -> Unit) {
    Box(Modifier.size(w * s, h * s), contentAlignment = Alignment.Center) {
        content(Modifier.requiredSize(w, h).graphicsLayer { scaleX = s; scaleY = s })
    }
}

/** Update 158: what the second screen shows while a twin layout is on: its bottom part, filling the screen. */
@Composable
internal fun TwinSecondScreen(c: StyleCtx) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Keep each layout's original category controls. This extra selector is opt-in.
            TwinSharedCategoryStrip(c)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (c.state.appearance.homeLayout) {
                    HomeLayout.TWIN_ICONS -> TwinIconsBottom(c, Modifier.fillMaxSize(), framed = false)
                    HomeLayout.TWIN_SHOWCASE -> TwinShowcaseBottom(c, Modifier.fillMaxSize(), framed = false)
                    HomeLayout.POCKET_CLASSIC -> PocketClassicBottom(c, Modifier.fillMaxSize(), framed = false)
                    HomeLayout.POCKET_RIBBON -> PocketRibbonBottom(c, Modifier.fillMaxSize(), framed = false)
                    else -> TwinDeskBottom(c, Modifier.fillMaxSize(), framed = false)
                }
            }
        }
        // The other four layouts already include a native dashboard button. Showcase did not.
        if (c.state.appearance.homeLayout == HomeLayout.TWIN_SHOWCASE &&
            com.shelf.launcher.data.Features.twinDashboardAccess.state.value
        ) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(50))
                    .background(Color(0xEAF8F9FB)).tapOnly { TwinScreens.dashboard = true }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Dashboard, "Normal second screen",
                    tint = Color(0xFF26313C), modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(5.dp))
                androidx.compose.material3.Text("Second screen", color = Color(0xFF26313C), fontSize = 11.sp)
            }
        }
    }
}

/** Optional, consistent category selection for layouts that use different built-in navigation. */
@Composable
private fun TwinSharedCategoryStrip(c: StyleCtx) {
    if (!com.shelf.launcher.data.Features.twinSharedCategories.state.value) return
    val mode = c.state.appearance.categoryBar
    if (mode == com.shelf.launcher.data.CategoryBar.OFF || c.stops.isEmpty()) return
    val expanded = mode == com.shelf.launcher.data.CategoryBar.FULL || TwinScreens.categoriesExpanded
    val accent = ShelfTheme.accent
    Box(
        Modifier.fillMaxWidth().height(38.dp).background(Color(0xFFF0F2F6))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (!expanded) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .tapOnly { TwinScreens.categoriesExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    c.categoryLabel, color = Color(0xFF26313C), fontSize = 12.sp,
                    modifier = Modifier.weight(1f), maxLines = 1
                )
                androidx.compose.material3.Text("Categories ›", color = Color(0xFF576473), fontSize = 11.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlidingChipRow(
                    count = c.stops.size, focused = c.stop, modifier = Modifier.weight(1f),
                    spacing = 5.dp, endPadding = 6.dp
                ) { i ->
                    val on = i == c.stop
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) accent else Color(0xFFDDE2E8))
                            .tapOnly {
                                val steps = pocketStepsTo(c, i)
                                if (steps != 0) c.actions.category(steps)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        androidx.compose.material3.Text(
                            c.stops[i].label,
                            color = if (on) onAccentColor() else Color(0xFF26313C),
                            fontSize = 11.sp, maxLines = 1
                        )
                    }
                }
                if (mode == com.shelf.launcher.data.CategoryBar.CURRENT) {
                    androidx.compose.material3.Text(
                        "Done", color = Color(0xFF26313C), fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .tapOnly { TwinScreens.categoriesExpanded = false }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Update 158: the time now, refreshed once a second while [seconds] (else once a minute) and only while Voltcap is
 * in front. Read it inside a draw block or a small composable, so only that redraws.
 */
@Composable
internal fun rememberTwinTime(seconds: Boolean): LongState {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val resumed = rememberIsResumed()
    LaunchedEffect(seconds, resumed) {
        if (resumed) {
            while (true) {
                val t = System.currentTimeMillis()
                now.longValue = t
                val step = if (seconds) 1_000L else 60_000L
                kotlinx.coroutines.delay(step - t % step + 15L)
            }
        }
    }
    return now
}

/**
 * Update 158: a list's scroll place (in rows) that glides to [target], or jumps there when it is more than [far]
 * rows away or the list changed size ([size]: a new category).
 */
@Composable
internal fun rememberTwinScroll(target: Float, far: Float, size: Int): Animatable<Float, AnimationVector1D> {
    val anim = remember { Animatable(target) }
    val seen = remember { intArrayOf(size) }
    LaunchedEffect(target, size) {
        val fresh = seen[0] != size
        seen[0] = size
        if (fresh || kotlin.math.abs(anim.value - target) > far) anim.snapTo(target)
        else anim.animateTo(target, tween(200, easing = FastOutSlowInEasing))
    }
    return anim
}
