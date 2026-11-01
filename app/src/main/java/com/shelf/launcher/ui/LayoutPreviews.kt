package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shelf.launcher.R
import com.shelf.launcher.data.HomeLayout

/*
 * Update 337: a small picture of each home layout (Settings › Home › Layout & presets, and the first-start setup's Your
 * home step), so you see what a layout looks like before switching to it. Taken in Voltcap with made-up games (plain
 * covers, no real game art); one per layout in res/drawable-nodpi/lp_<layout>.webp.
 *
 * Update 423: all 53 taken again on the AYN Thor (1920 × 1080) from the current designs with the made-up stand-in library
 * (DemoLibrary, `--ez voltcap_test_demo true`), scaled to 640 × 360 — the screen's own 16:9, nothing stretched or borrowed.
 * Every layout must have one: the `when` has no else branch (a new layout doesn't compile without its picture) and
 * LayoutPreviews423Test checks that each file is there.
 */

/** [l]'s picture. */
internal fun layoutPreviewRes(l: HomeLayout): Int = when (l) {
    HomeLayout.CLASSIC -> R.drawable.lp_classic
    HomeLayout.TILES -> R.drawable.lp_tiles
    HomeLayout.HERO -> R.drawable.lp_hero
    HomeLayout.GRID -> R.drawable.lp_grid
    HomeLayout.TV -> R.drawable.lp_tv
    HomeLayout.DASH -> R.drawable.lp_dash
    HomeLayout.LIVE -> R.drawable.lp_live
    HomeLayout.SIDE -> R.drawable.lp_side
    HomeLayout.CROSS -> R.drawable.lp_cross
    HomeLayout.SPOT -> R.drawable.lp_spot
    HomeLayout.BAR -> R.drawable.lp_bar
    HomeLayout.SHELF -> R.drawable.lp_shelf
    HomeLayout.HUB -> R.drawable.lp_hub
    HomeLayout.RESUME -> R.drawable.lp_resume
    HomeLayout.CHAN -> R.drawable.lp_chan
    HomeLayout.CIRCUIT -> R.drawable.lp_circuit
    HomeLayout.LIBRARY -> R.drawable.lp_library
    HomeLayout.STACKS -> R.drawable.lp_stacks
    HomeLayout.TIMELINE -> R.drawable.lp_timeline
    HomeLayout.ARCADE -> R.drawable.lp_arcade
    HomeLayout.CONSOLES -> R.drawable.lp_consoles
    HomeLayout.DISCS -> R.drawable.lp_discs
    HomeLayout.HERO_TILES -> R.drawable.lp_hero_tiles
    HomeLayout.TWIN_DESK -> R.drawable.lp_twin_desk
    HomeLayout.TWIN_ICONS -> R.drawable.lp_twin_icons
    HomeLayout.TWIN_SHOWCASE -> R.drawable.lp_twin_showcase
    HomeLayout.CRATE -> R.drawable.lp_crate
    HomeLayout.CARDS -> R.drawable.lp_cards
    HomeLayout.CUSTOM -> R.drawable.lp_custom
    HomeLayout.POCKET_CLASSIC -> R.drawable.lp_pocket_classic
    HomeLayout.POCKET_RIBBON -> R.drawable.lp_pocket_ribbon
    HomeLayout.GREENCORE -> R.drawable.lp_greencore
    HomeLayout.POCKET -> R.drawable.lp_pocket
    HomeLayout.CUBE -> R.drawable.lp_cube
    HomeLayout.MEMCARD -> R.drawable.lp_memcard
    HomeLayout.CASEFLOW -> R.drawable.lp_caseflow
    HomeLayout.BOX -> R.drawable.lp_box
    HomeLayout.STORAGE -> R.drawable.lp_storage
    HomeLayout.FILESELECT -> R.drawable.lp_fileselect
    HomeLayout.ORBIT -> R.drawable.lp_orbit
    HomeLayout.AIRY -> R.drawable.lp_airy
    HomeLayout.NIGHT -> R.drawable.lp_night
    HomeLayout.BLADES -> R.drawable.lp_blades
    HomeLayout.BUBBLES -> R.drawable.lp_bubbles
    HomeLayout.CAMERA -> R.drawable.lp_camera
    HomeLayout.PANELS -> R.drawable.lp_panels
    HomeLayout.MAGAZINE -> R.drawable.lp_magazine
    HomeLayout.SHOP -> R.drawable.lp_shop
    HomeLayout.TERMINAL -> R.drawable.lp_terminal
    HomeLayout.CINEMA -> R.drawable.lp_cinema
    HomeLayout.ARCTIC -> R.drawable.lp_arctic
    HomeLayout.COVERDETAILS -> R.drawable.lp_coverdetails
    HomeLayout.ARTLIB -> R.drawable.lp_artlib
}

/** [l]'s picture, rounded, as wide as it may be (up to 420 dp). Nothing for a layout without one. */
@Composable
internal fun LayoutPreview(l: HomeLayout, modifier: Modifier = Modifier) {
    val res = layoutPreviewRes(l)
    val shape = RoundedCornerShape(10.dp)
    Image(
        painterResource(res), l.label + " preview",
        contentScale = ContentScale.Crop,
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(16f / 9f)
            .clip(shape).border(1.dp, Color.White.copy(alpha = 0.14f), shape)
    )
}
