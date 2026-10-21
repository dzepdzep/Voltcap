package com.shelf.launcher.ui

import com.shelf.launcher.data.Game

/*
 * Update 305: does a picture you add fit what it's going on? Its shape is compared with the surface's real shape — this
 * game's case front, its whole wrap (back + spine + front), its back, its spine, a box side, its disc or cartridge
 * label — from Voltcap's own model sizes (CaseCatalog, the media shells). A picture made for another format (a Universal
 * Game Case wrap on an N64 box, a DVD cover on a Switch case) is said to be so when you add it, and what Voltcap does
 * with it: the model never changes, the picture is cut in the middle (fronts, wraps' fronts, spines) or fitted (backs,
 * box sides) — the size of a picture alone isn't taken to say which console or format it's for.
 */
internal object ArtFit {
    /** Formats a picture can say it was made for (ArtProvenance.madeFor), offered when you add one. */
    val FORMATS = listOf(
        "This game's own case / box", "Universal Game Case / DVD case", "Blu-ray case (PS3–PS5, Xbox One)",
        "Nintendo Switch case", "DS / 3DS case", "CD jewel case", "Cartridge label", "Disc label", "Other"
    )

    /** The shape (width / height) [label] has on [g]'s model, and what that is, or null when it can't be told. */
    fun expected(label: String, g: Game): Pair<Float, String>? {
        val spec by lazy { CaseCatalog.forGame(g) }
        val caseName by lazy { spec?.let { "${com.shelf.launcher.data.ConsoleNames.display(it.platform)} ${shellName(it.shell)} (${mm(it.wMm)} × ${mm(it.hMm)} × ${mm(it.tMm)} mm)" } }
        return when (label) {
            "Box" -> spec?.let { it.wMm / it.hMm to "this game's $caseName front" }
            "Back of the box" -> spec?.let { it.wMm / it.hMm to "this game's $caseName back" }
            BoxTexture.LABEL -> spec?.let { (2 * it.wMm + it.tMm) / it.hMm to "this game's $caseName laid out flat (back + spine + front)" }
            "Spine" -> spec?.let { it.tMm / it.hMm to "this game's $caseName spine" }
            "Box right side" -> spec?.let { it.tMm / it.hMm to "this box's right side" }
            "Box top", "Box bottom" -> spec?.let { it.wMm / it.tMm to "this box's ${if (label == "Box top") "top" else "bottom"}" }
            "Disc label" -> MediaShell.of(g.platform)?.let { m ->
                if (m.isDisc) 1f to "a round disc label"
                else {
                    // (The drawn cartridge's own label — its real shell, or the one picked for this game — CartShells.)
                    val sh = runCatching { ownShellOf(g.platform, g.stableKey) }.getOrNull()
                    if (sh != null && sh.label[3] > 0f) sh.label[2] / sh.label[3] to "this cartridge's label (${mm(sh.label[2])} × ${mm(sh.label[3])} mm)"
                    else ((m.label[2] - m.label[0]) * m.aspect / (m.label[3] - m.label[1])) to "this cartridge's label window"
                }
            }
            else -> null
        }
    }

    /**
     * A note when a [w] × [h] picture for [label] is far off [g]'s surface (more than [tolerance]), or null when it fits.
     * Pictures that may lie either way (a box side photographed lying down) are compared both ways.
     */
    fun warning(label: String, w: Int, h: Int, g: Game, tolerance: Float = 0.12f): String? {
        if (w <= 0 || h <= 0) return null
        val (want, what) = expected(label, g) ?: return null
        val have = w.toFloat() / h
        val off = kotlin.math.abs(have / want - 1f)
        val sideways = label.startsWith("Box ") && label != BoxTexture.LABEL && kotlin.math.abs((1f / have) / want - 1f) <= tolerance
        if (off <= tolerance || sideways) return null
        val how = when (label) {
            "Box", "Spine" -> "It's cut in the middle to fit, never stretched — part of it won't show."
            BoxTexture.LABEL -> "Its front is cut to the case's shape, never stretched; check where it's cut with Adjust the cuts…."
            "Disc label" -> "The disc or cartridge keeps its own shape; the picture is cut in the middle to its label, never stretched."
            "Back of the box" -> "Up to about a fifth off, it's eased onto the back as back scans are; further off, it's cut in the middle to the back's shape."
            else -> "It's fitted onto it whole, the rest in its edge colour — never stretched."
        }
        return "This picture's shape (%.2f : 1) doesn't match %s (%.2f : 1) — it may have been made for another case or format. %s".format(java.util.Locale.US, have, what, want, how)
    }

    private fun mm(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)

    private fun shellName(s: CaseShell): String = when (s) {
        CaseShell.KEEP -> "case"; CaseShell.JEWEL -> "jewel case"; CaseShell.CARDCASE -> "card case"
        CaseShell.CLAMSHELL -> "clamshell"; else -> "box"
    }
}
