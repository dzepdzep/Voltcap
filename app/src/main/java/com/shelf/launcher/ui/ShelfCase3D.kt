package com.shelf.launcher.ui

/*
 * Shelf 3D work (12 Nov, experimental): the Game shelf's pulled-out case as ONE rigid 3D case (Case3D: real front, spine, top,
 * bottom, edges and back, one camera, light by face) instead of two flat pictures turned side by side (Batch 523's orthographic
 * "one box": the spine d·cos t and the front w·sin t next to each other - no perspective, no top, no light, so it read as cards).
 *
 * The pose is pure (ShelfCasePose / shelfCasePose, unit-tested): the case turns round its own upright through its middle, as a
 * case turned in the hand, from its spine facing you (yaw 90) to its front (yaw 0), at one size while it turns, then grows
 * uniformly (rigid: width, height and depth together) to its big place. A small tip toward you shows its top while it turns
 * and is level at both ends. The perspective's enlargement of the near face is taken out, so at the hand-over the 3D spine
 * covers exactly the shelf's spine slot and at the end the front is exactly the big front.
 *
 * Internal switch, not a setting (owner rule: no new toggles as fixes): off on the stable branch; tests, the QA app's hook
 * (`--ez voltcap_test_shelf3d true`) and the experimental branch turn it on. Visual acceptance needs a handheld.
 */
internal object Shelf3D {
    /** On: the Game shelf's pulled-out case is the 3D case. */
    @Volatile var enabled = false

    /** Games whose pulled-out case is the 3D one right now (the shelf hides their own spine meanwhile). Read while drawing. */
    val active = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun on(key: String?): Boolean = key != null && active[key] == true

    /**
     * Hand-over (12 Nov): the shelf's own spine (its picture, its drawn top and shading) stays on screen through the rise and
     * cross-fades into the 3D case over the first [BLEND] of the turn, while the two still all but coincide - so the frame the
     * 3D case takes over is exactly the shelf's picture (no pop of the top cap, the shading or the thickness).
     */
    const val BLEND = 0.2f

    /** How much of the 3D case shows at [turn] (0 … 1 over the first [BLEND] of it). Pure. */
    fun blendIn(turn: Float): Float {
        val x = (turn / BLEND).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * The shelf's own spine under the 3D case: whole until the 3D case on top of it is whole, then gone (12 Nov: a cross-fade
     * of two half-transparent layers let the dark background through - a dip in brightness mid-hand-over). Pure.
     */
    fun shelfSpineAlpha(turn: Float): Float = if (blendIn(turn) >= 1f) 0f else 1f

    /**
     * Hardware QA, 12 Nov (Flip 2): the 3D case draws its pictures by Case3D's own rules, the shelf by its own. For a game
     * without scans the shelf shows a strip of its cover with the name up it and pulls out the console's case art, while
     * Case3D drew the console's coloured spine and the bare cover in a plain shell: a different spine and a different front
     * from the hand-over on (an artwork substitution). So the 3D case takes a pull only where both draw the same pictures:
     * the game's own spine (your cut, its spine scan, or the strip from its box scan with Original cases on) and its own
     * scanned front with Real case + Original cases on, no case or spine picked for all, no Matching spines. Anywhere else
     * the shelf's own path runs for that pull, unchanged. Pure apart from the settings it reads.
     */
    fun sameArtSources(platform: String?, physical: List<Pair<String, String>>?): Boolean {
        if (physical == null) return false                                       // scans not known yet: the shelf's own path
        if (!com.shelf.launcher.data.Features.originalCases.state.value) return false
        if (com.shelf.launcher.data.Features.shelfPulled.state.value != com.shelf.launcher.data.Features.ShelfPulled.FRONT) return false
        if (com.shelf.launcher.data.Features.spinesOwn.state.value || LookStyles.spineForAll() || LookStyles.caseForAll()) return false
        if (androidOwnCase(platform)) return false
        return artSourcesAgree(physical, physical.withoutFlat(), platform)
    }

    /** The shelf's spine and front (from [shelf], its scans as they are) against Case3D's (from [case3d], without flat stand-ins). */
    internal fun artSourcesAgree(shelf: List<Pair<String, String>>?, case3d: List<Pair<String, String>>?, platform: String?): Boolean {
        val shelfSpine = BoxTexture.userSpine(shelf, platform) ?: shelf?.firstOrNull { it.first == "Spine" }?.second ?: BoxTexture.spine(shelf, platform)
        val caseSpine = BoxTexture.userSpine(case3d, platform) ?: case3d?.firstOrNull { it.first == "Spine" }?.second ?: BoxTexture.spine(case3d, platform)
        val shelfFront = BoxTexture.front(shelf, platform)
        val caseFront = BoxTexture.front(case3d, platform)
        return shelfSpine != null && shelfSpine == caseSpine && shelfFront != null && shelfFront == caseFront
    }

    /**
     * Hardware QA, 12 Nov (Flip 2): Case3D gives the case its real shape (CaseCatalog) and cuts the front scan to it; the shelf
     * shows the scan at its own shape. Within 8 % the cut is invisible; beyond (a portrait cover on a square jewel case) the
     * front lost its title band - so the 3D case takes the pull only within that. Pure.
     */
    fun shapeAgrees(scanShape: Float, caseAspect: Float?): Boolean =
        caseAspect == null || kotlin.math.abs(scanShape / caseAspect.coerceIn(0.55f, 1.85f) - 1f) <= 0.08f

    /** The width / height each game's spine picture is drawn at on the shelf (SpineSlot), for the 3D case's thickness. */
    val drawnDepth = HashMap<String, Float>()

    /** Camera of Case3D (CaseDraw.draw: focal = h × FOCAL). */
    const val FOCAL = 3.4f

    /** Most the case tips toward you mid-turn (degrees); level at both ends. Over atan(1 / (2 × FOCAL)) ≈ 8.4°, so its top shows (the camera is level with its middle). */
    const val TURN_PITCH = 11f

    /** Decision for one pull-out: undecided (3D case loading, invisible), the 3D case, or the flat path for this pull. */
    enum class Mode { WAITING, CASE_3D, FLAT }

    /**
     * The hand-over rule (pure): the 3D case takes over only once it is drawn whole ([ready]) and while it still faces you with
     * its spine ([turn] = 0) - where it looks exactly like the shelf's spine; a case not ready by the time the turn starts
     * keeps the flat path for this whole pull (never a swap mid-turn).
     */
    fun decide(now: Mode, ready: Boolean, turn: Float): Mode = when {
        now != Mode.WAITING -> now
        ready && turn <= 0f -> Mode.CASE_3D
        turn > 0f -> Mode.FLAT
        else -> Mode.WAITING
    }
}

/** Set round a shelf spine while the 3D case is on: SpineSlot reports the width / height its picture is drawn at. */
internal val LocalSpineDrawn = androidx.compose.runtime.staticCompositionLocalOf<((Float) -> Unit)?> { null }

/** Where and how the pulled-out case stands at one moment (px and degrees). [h]: the height to draw the case at (Case3D). */
internal data class ShelfCasePose(val yaw: Float, val pitch: Float, val h: Float, val cx: Float, val cy: Float, val apparentH: Float)

/**
 * The pulled-out case's pose at pull [p] (0 on the shelf … 1 out and facing you). [slotCx] / [slotBottom] / [slotH]: its spine's
 * place on the shelf; [bigCx] / [bigTop] / [bigH]: its big place; [shape]: front width / height; [lift]: how far it rises out
 * of its gap (share of its height); [slide] / [turn] / [grow]: the pull's phases (ShelfHome: shelfSlide / shelfTurn / shelfGrow).
 * Pure.
 */
internal fun shelfCasePose(
    p: Float, slotCx: Float, slotBottom: Float, slotH: Float, bigCx: Float, bigTop: Float, bigH: Float,
    shape: Float, depth: Float, lift: Float, slide: (Float) -> Float, turn: (Float) -> Float, grow: (Float) -> Float
): ShelfCasePose {
    val s = slide(p); val t = turn(p); val gr = grow(p)
    val yaw = 90f * (1f - t)
    val pitch = Shelf3D.TURN_PITCH * kotlin.math.sin(Math.PI * t).toFloat()
    // The size it should LOOK (its near face): the shelf's spine height, then growing to the big front's.
    val apparent = slotH + (bigH - slotH) * gr
    val h = apparent * nearFaceCorrection(yaw, shape, depth)
    // The case's middle: on its slot while it rises and turns, then to its big place.
    val restTop = slotBottom - slotH - slotH * lift * s
    val top = restTop + (bigTop - restTop) * gr
    val cx = slotCx + (bigCx - slotCx) * gr
    return ShelfCasePose(yaw, pitch, h, cx, top + apparent / 2f, apparent)
}

/**
 * Case3D draws its near face larger than its height (perspective: that face is nearer the eye than the case's middle, by half
 * its width when the spine faces you, half its depth when the front does). The height to draw at so the near face looks
 * [apparent] tall is apparent × this. Pure.
 */
internal fun nearFaceCorrection(yaw: Float, shape: Float, depth: Float): Float {
    val a = Math.toRadians(yaw.toDouble())
    val near = (shape / 2f) * kotlin.math.abs(kotlin.math.sin(a)).toFloat() + (depth / 2f) * kotlin.math.abs(kotlin.math.cos(a)).toFloat()
    return (1f - near / Shelf3D.FOCAL).coerceIn(0.5f, 1f)
}

/**
 * Projected corners of the case's faces at a pose (the same camera as CaseDraw, pitch included): for the geometry tests. Returns
 * per face (front, spine, edge, top, back, bottom) its 4 screen points and whether it faces the camera.
 */
internal fun shelfCaseFaces(w: Float, h: Float, d: Float, yaw: Float, pitch: Float): List<Pair<FloatArray, Boolean>> {
    val faces = arrayOf(
        floatArrayOf(-1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f),      // front
        floatArrayOf(-1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f),  // spine
        floatArrayOf(1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f),      // edge
        floatArrayOf(-1f, -1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, -1f, -1f, 1f),  // top
        floatArrayOf(1f, -1f, -1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f),  // back
        floatArrayOf(-1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, -1f, 1f, -1f)       // bottom
    )
    val ty = Math.toRadians(yaw.toDouble()); val tp = Math.toRadians(pitch.toDouble())
    val cy0 = kotlin.math.cos(ty).toFloat(); val sy0 = kotlin.math.sin(ty).toFloat()
    val cp = kotlin.math.cos(tp).toFloat(); val sp = kotlin.math.sin(tp).toFloat()
    val focal = h * Shelf3D.FOCAL
    return faces.map { f ->
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val x = f[i * 3] * w / 2f; val y = f[i * 3 + 1] * h / 2f; val z = f[i * 3 + 2] * d / 2f
            val x1 = x * cy0 + z * sy0; val z1 = -x * sy0 + z * cy0
            val y2 = y * cp + z1 * sp; val z2 = -y * sp + z1 * cp
            val k = focal / (focal - z2)
            out[i * 2] = x1 * k; out[i * 2 + 1] = y2 * k
        }
        var a = 0f
        for (i in 0 until 4) { val j = (i + 1) % 4; a += out[i * 2] * out[j * 2 + 1] - out[j * 2] * out[i * 2 + 1] }
        out to (a > 0f)
    }
}
