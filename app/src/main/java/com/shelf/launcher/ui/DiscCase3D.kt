package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features

/*
 * Update 151: "3D case + disc" (Settings › Game look › Cases with a disc, and a step of the R1 Game look tile). Like
 * "Case + disc" (DiscInCase, the flat case with its disc half out of it), but the case is the 3D box from the template
 * pack (for every console alike; DiscIn3DModelCase only for CaseArt's models3d) — with the disc behind it, half out of its
 * right side, turning as set (Moving discs, cartridges & cases). A PlayStation (PS1) case without a 3D model keeps the
 * flat case (its 3D box shows the black band, update 143).
 */

/** Update 151: the 3D case + disc is on, and the pack has a disc for [platform] (the console whose case is drawn). */
internal fun disc3dOn(platform: String?): Boolean =
    platform != null && Features.discCase3d.state.value && Features.discCase.state.value &&
        MediaTemplates.of(platform)?.disc != null

// (Update 200: modelDiscFrac and DiscIn3DModelCase — the shape library's pre-rendered 3D case with its disc — are gone:
// the pre-rendered 3D models no longer draw games.)

/**
 * Update 151: how far right past its case the disc of a 3D case + disc sticks out (a share of the case's height), for
 * the room the row leaves after the game you're on (casePeek); null when this look isn't drawn for [p].
 */
internal fun disc3dPeek(p: String): Float? {
    if (!disc3dOn(p)) return null
    // (Update 151: the case looks never stand a 3D model case — CaseArt draws the template's 3D box for every console.)
    if (p == "PS1") return null
    val shell = MediaShell.of(p)
    if (MixConsoles.caseWithMedia(p) && shell != null) return null
    val box = MediaTemplates.of(p)?.box ?: return null
    return discFrac(p, box.aspect, box, false) * DISC_OUT
}

/** Update 151: the disc behind the case: the game's own disc picture when there is one, else the pack's disc with its art. */
@Composable
private fun DiscBehind(tpl: MediaTpl?, realDisc: Any?, art: Any?, modifier: Modifier, seed: Int) {
    Box(modifier.mediaMotion(true, seed)) {
        if (realDisc != null) AsyncImage(
            // (Update 157; 159: the scale and size were inside this comment, so the disc scan drew at its own size.)
            mediaArtModel(realDisc), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
        else if (tpl?.disc != null) TemplateMedia(MediaTpl(null, null, tpl.disc, null), labelScan = null, cover = art, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Update 151: the template pack's 3D box with the disc half out of it. [cover] goes into the console's cover frame,
 * [scan] (the game's own case front) is the box's front with Original cases on; [scanOk]: it loaded before.
 */
@Composable
internal fun DiscInBox3D(
    tpl: MediaTpl, cover: Any?, scan: Any?, scanOk: Boolean, modifier: Modifier, title: String, detail: Int, platform: String?
) {
    val box = tpl.box ?: return
    val own = Features.originalCases.state.value && scanOk && scan != null
    val (front, isBox) = if (own) scan to true else boxFront(box, cover, scan)
    val realDisc = BoxTexture.mediaFor(scan)
    val seed = (cover?.hashCode() ?: 0) xor (box.res * 31)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = box.aspect
        // (Update 331: case and disc fit the slot together — the disc was behind the next game, or cut off.)
        val dFrac = discFrac(platform, aspect, box, false)
        val h = minOf(maxHeight, maxWidth / (aspect + dFrac * DISC_OUT))
        val d = h * dFrac
        val lead = (maxWidth - (h * aspect + d * DISC_OUT)) / 2
        Box(
            Modifier.align(Alignment.CenterStart).wrapContentSize(Alignment.CenterStart, unbounded = true)
                .offset(x = lead).size(h * aspect + d * DISC_OUT, h)
        ) {
            DiscBehind(tpl, realDisc, cover ?: scan, Modifier.size(d).align(Alignment.CenterEnd), seed)
            Box3D(
                box, front, isBox, title, Modifier.size(h * aspect, h).align(Alignment.CenterStart), spin = false, detail = detail,
                realSpine = if (own) BoxTexture.spineFor(scan) else null
            )
        }
    }
}

