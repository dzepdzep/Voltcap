package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import com.shelf.launcher.data.Features

/*
 * Update 158: "The one you're on" - the 3D piece or case you're on faces you straight on (the default), or stands
 * turned like the others. One setting for every 3D view: the 3D models look (its Stand), the carousels' 3D cases
 * (Arc, Spine wall, Turned row, the "3D cases" switch) and Case stacks' 3D case. The others keep their own turn.
 */
internal object PickedFacing {
    /** How far the one you're on is turned with "Turned like the others" (degrees; its left side / spine shows). */
    const val TURN = 24f

    /** True: the one you're on faces you. Read inside composables (it recomposes when it changes). */
    val faces: Boolean get() = Features.pickedFaces.state.value

    /**
     * The extra turn of a 3D case or piece at distance [d] from the middle of a carousel: none while it faces you; with
     * "Turned like the others", [TURN] at the middle, fading out over one step so a sliding row stays smooth.
     */
    fun carouselTurn(d: Float): Float = if (faces) 0f else TURN * (1f - d).coerceIn(0f, 1f)

    /** Sets it, and keeps the 3D models' Stand in step (Turned ⇄ Turned, yours facing you); Straight on stays as it is. */
    fun set(faces: Boolean) {
        Features.pickedFaces.value = faces
        val a = Features.modelAngle.value
        if (faces && a == ModelAngle.TURNED) Features.modelAngle.value = ModelAngle.FACE_PICKED
        if (!faces && a == ModelAngle.FACE_PICKED) Features.modelAngle.value = ModelAngle.TURNED
    }
}

/** Update 158: the "The one you're on" choice (Game look › 3D models, Carousel options, Case stacks options). */
@Composable
internal fun PickedFacingChoice(note: Boolean = true) {
    SectionLabel("The one you're on")
    ChipFlow {
        val on = Features.pickedFaces.state.value
        Chip((if (on) "✓ " else "") + "Faces you", accent = on) { PickedFacing.set(true) }
        Chip((if (!on) "✓ " else "") + "Turned like the others", accent = !on) { PickedFacing.set(false) }
    }
    if (note) MutedNote(
        "Faces you: the 3D piece or case you're on stands straight on, the others keep their turn. " +
            "The same for the 3D models, the carousels' 3D cases and Case stacks."
    )
}
