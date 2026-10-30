package com.shelf.launcher.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 410: full / compat variant detection, asset choice (API list + release-page fallback), migration and the no-silent-swap rule. */
class UpdateVariantTest {
    private val new = listOf("Voltcap-0.6.61.apk", "Voltcap-0.6.61-compat.apk", "voltcap-profile-api31.dm")
    private val newCompatFirst = listOf("Voltcap-0.6.61-compat.apk", "Voltcap-0.6.61.apk")
    private val legacy = listOf("Voltcap-0.6.60.apk", "Voltcap-0.6.60-notify.apk")

    @Test fun detectsTheInstalledVariantFromTheListener() {
        assertEquals(AppVariant.FULL, UpdateVariant.detect(true))
        assertEquals(AppVariant.COMPAT, UpdateVariant.detect(false))
    }

    @Test fun fullUsersStayFullAndCompatUsersStayCompat() {
        assertEquals("Voltcap-0.6.61.apk", UpdateVariant.pick("v0.6.61", new, UpdateVariant.wanted(AppVariant.FULL, null)))
        assertEquals("Voltcap-0.6.61-compat.apk", UpdateVariant.pick("v0.6.61", new, UpdateVariant.wanted(AppVariant.COMPAT, null)))
        // asset order in the release does not matter
        assertEquals("Voltcap-0.6.61.apk", UpdateVariant.pick("v0.6.61", newCompatFirst, AppVariant.FULL))
        assertEquals("Voltcap-0.6.61-compat.apk", UpdateVariant.pick("v0.6.61", newCompatFirst, AppVariant.COMPAT))
    }

    @Test fun onlyAnExplicitSwitchChangesTheVariant() {
        assertEquals(AppVariant.FULL, UpdateVariant.wanted(AppVariant.COMPAT, AppVariant.FULL))
        assertEquals(AppVariant.COMPAT, UpdateVariant.wanted(AppVariant.FULL, AppVariant.COMPAT))
    }

    @Test fun neverFallsBackToTheOtherVariant() {
        assertNull(UpdateVariant.pick("v0.6.61", listOf("Voltcap-0.6.61.apk"), AppVariant.COMPAT))
        assertNull(UpdateVariant.pick("v0.6.61", listOf("Voltcap-0.6.61-compat.apk"), AppVariant.FULL))
        assertNull(UpdateVariant.pick("v0.6.61", listOf("voltcap-profile-api31.dm"), AppVariant.FULL))
    }

    @Test fun legacyNamingMapsPlainToCompatAndNotifyToFull() {
        // migration: releases up to 0.6.60 shipped the build WITHOUT the listener as the plain name
        assertEquals("Voltcap-0.6.60.apk", UpdateVariant.pick("v0.6.60", legacy, AppVariant.COMPAT))
        assertEquals("Voltcap-0.6.60-notify.apk", UpdateVariant.pick("v0.6.60", legacy, AppVariant.FULL))
        assertNull(UpdateVariant.pick("v0.6.60", listOf("Voltcap-0.6.60.apk"), AppVariant.FULL))
    }

    @Test fun debugApksAreNeverOffered() {
        assertNull(UpdateVariant.pick("v0.6.61", listOf("Voltcap-0.6.61-debug.apk"), AppVariant.FULL))
    }

    @Test fun releasePageFallbackNamesBothVariantsAndBothSchemes() {
        assertEquals("Voltcap-0.6.61.apk", UpdateVariant.fallbackName("v0.6.61", AppVariant.FULL))
        assertEquals("Voltcap-0.6.61-compat.apk", UpdateVariant.fallbackName("v0.6.61", AppVariant.COMPAT))
        assertEquals("Voltcap-0.7.0.apk", UpdateVariant.fallbackName("v0.7.0", AppVariant.FULL))
        assertEquals("Voltcap-0.6.60.apk", UpdateVariant.fallbackName("v0.6.60", AppVariant.COMPAT))
        assertEquals("Voltcap-0.6.60-notify.apk", UpdateVariant.fallbackName("v0.6.60", AppVariant.FULL))
        assertTrue(UpdateVariant.atLeast("0.10.0", "0.6.61"))
        assertFalse(UpdateVariant.atLeast("0.6.60", "0.6.61"))
    }

    /** Both update paths (API list and release page) must go through the variant choice, and the same-version switch is offered. */
    @Test fun updaterUsesTheVariantOnEveryPath() {
        val src = listOf("src/main/java/com/shelf/launcher/data/AppUpdates.kt", "../src_copy/data/AppUpdates.kt")
            .map(::File).first { it.isFile }.readText()
        assertTrue(src.contains("UpdateVariant.pick("))
        assertTrue(src.contains("UpdateVariant.fallbackName("))
        assertFalse("the API path must not take 'the first apk' any more", src.contains("n.endsWith(\".apk\", ignoreCase = true) && !n.contains"))
        assertTrue(src.contains("wantedVariant(app) != installedVariant(app) && !newer(have, tag)"))
    }
}
