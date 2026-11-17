package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 675c: a QaClip dump says whether the app's own game window is up (and how long) and which Compose roots it knows. */
class QaClipDiag675cTest {
    @Test fun popupAgeOnlyWhileAWindowIsUp() {
        assertNull(QaClip.popupAge(open = false, openedAt = 1_000L, now = 5_000L))
        assertNull(QaClip.popupAge(open = true, openedAt = 0L, now = 5_000L))
        assertEquals(4_000L, QaClip.popupAge(open = true, openedAt = 1_000L, now = 5_000L))
        assertEquals(0L, QaClip.popupAge(open = true, openedAt = 6_000L, now = 5_000L))
    }

    @Test fun rootJsonNamesItsState() {
        assertEquals("{\"id\":\"e8df3d8\",\"attached\":true,\"shown\":false,\"focus\":false}",
            QaClip.rootJson("e8df3d8", attached = true, shown = false, focus = false))
        assertEquals("{\"id\":\"a\\\"b\",\"attached\":false,\"shown\":false,\"focus\":true}",
            QaClip.rootJson("a\"b", attached = false, shown = false, focus = true))
    }
}
