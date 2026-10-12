package com.shelf.launcher.ui

import com.shelf.launcher.R
import com.shelf.launcher.data.Features

/**
 * Variants on top of the generated template pack (MediaTemplates.kt): Game Boy Color games as the black GBC-only
 * cartridge (the Game Boy shell recoloured, res tpl_cart_gbc_black / tpl_cartl_gbc_black) or the grey dual-mode
 * one (the Game Boy shell as it is), besides the pack's clear GBC shell (Appearance › Game row › Cartridge look).
 */
internal object MediaVariants {
    /** The pack's entry (with the Game Boy Color variants), its box carrying the console's cover template and spine. */
    fun pick(platform: String, all: Map<String, MediaTpl>): MediaTpl? {
        if (platform == GENERIC_CART) return genericCart
        if (platform == ANDROID_CART) return androidCart
        if (platform.startsWith(GENERIC_DISC)) return genericDisc
        val t = pickBase(platform, all) ?: return null
        val box = t.box ?: return t
        if (!Features.coverTemplates.state.value) return t
        val cov = MediaCovers.of(platform) ?: return t
        return MediaTpl(t.cart, t.label, t.disc, covered.getOrPut(platform) {
            // (Update 125: your own console spine on the box's side when there is one.)
            BoxTpl(box.res, box.aspect, box.front, box.spine, box.hSpine, CustomSpines.of(platform)?.res ?: cov.spineRes, cov.frontRes, cov.frontAspect, cov.win)
        })
    }

    /**
     * The generic cartridge (the Game Boy shell with its "Nintendo GAME BOY" lettering removed, res tpl_cart_generic) and
     * the generic CD (the PS2 disc, a full-size 12 cm one): for games whose console has no cartridge / CD of its own
     * (Appearance › Game row › Mixed categories: All cartridges / All CDs). The label goes in the same window.
     */
    private val genericCart = MediaTpl(
        CartTpl(R.drawable.tpl_cart_generic, R.drawable.tpl_cartm_gb, 0.885f, floatArrayOf(0.145f, 0.3009f, 0.8483f, 0.851f)), null, null, null
    )
    private val genericDisc = MediaTpl(null, null, R.drawable.tpl_disc_ps2, null)
    /** Update 125: Android games' cartridge, the generic one in green, the label in the same window. */
    private val androidCart = MediaTpl(
        CartTpl(R.drawable.tpl_cart_android, R.drawable.tpl_cartm_gb, 0.885f, floatArrayOf(0.145f, 0.3009f, 0.8483f, 0.851f)), null, null, null
    )

    private val covered = java.util.concurrent.ConcurrentHashMap<String, BoxTpl>()

    private fun pickBase(platform: String, all: Map<String, MediaTpl>): MediaTpl? {
        val base = all[platform]
        if (platform != "GBC" || base == null) return base
        val gb = all["GB"] ?: return base
        return when (Features.gbcCart.state.value) {
            Features.GbcCart.CLEAR -> base
            Features.GbcCart.GREY -> MediaTpl(gb.cart, gb.label, base.disc, base.box)
            Features.GbcCart.BLACK -> MediaTpl(
                gb.cart?.let { CartTpl(R.drawable.tpl_cart_gbc_black, it.mask, it.aspect, it.win) },
                gb.label?.let { CartTpl(R.drawable.tpl_cartl_gbc_black, it.mask, it.aspect, it.win) },
                base.disc, base.box
            )
        }
    }
}
