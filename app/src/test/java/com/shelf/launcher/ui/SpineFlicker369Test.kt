package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 369: a custom (own Steam / Android) spine keeps showing for the same game while its accent colour changes. */
class SpineFlicker369Test {
    private val base = SpineCache.key(7, false, "Hades", 3)

    @Test fun sameGameWithAnotherAccentIsTheSameSpine() {
        assertTrue(SpineCache.sameOwnSpine("$base|own2|", base))
        assertTrue(SpineCache.sameOwnSpine("$base|own2|file:///covers/a.jpg", base))
    }

    @Test fun anotherGameOrTitleOrPackIsNot() {
        assertFalse(SpineCache.sameOwnSpine(SpineCache.key(7, false, "Hades II", 3) + "|own2|x", base))
        assertFalse(SpineCache.sameOwnSpine(SpineCache.key(8, false, "Hades", 3) + "|own2|x", base))
        assertFalse(SpineCache.sameOwnSpine(SpineCache.key(7, false, "Hades", 4) + "|own2|x", base))
        assertFalse(SpineCache.sameOwnSpine(SpineCache.key(7, true, "Hades", 3) + "|own2|x", base))
        assertFalse(SpineCache.sameOwnSpine(base, base)) // a console spine key has no own suffix
    }

    @Test fun keyKeepsTheGameIdentity() {
        assertEquals("7|false|3|Hades", base)
    }
}
