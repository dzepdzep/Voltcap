package com.shelf.launcher.device.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 348: which app each screen shows, read from the window manager's task list (as dumped on the AYN Thor). */
class ScreenAppsTest {
    private val own = "com.shelf.launcher"

    @Test fun underVoltcapsHomeTheLastAppUsed() {
        val lines = listOf(
            "Display #0 (activities from top to bottom):",
            "  * Task{a98ac29 #1146 type=home U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}",
            "    * Task{82bfbae #1147 type=home A=10207:com.shelf.launcher U=0 rootTaskId=1146 visible=true visibleRequested=true mode=fullscreen sz=1}",
            "  * Task{89a18a9 #1151 type=standard A=10130:dev.eden.eden_emulator U=0 visible=false visibleRequested=false mode=fullscreen sz=1}",
            "  * Task{b4f3eff #1073 type=standard I=com.android.launcher3/.uioverrides.QuickstepLauncher U=0 visible=false sz=1}",
            "Display #4 (activities from top to bottom):",
            "  * Task{18495c0 #1037 type=home U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}",
            "    * Task{3eedfa1 #1038 type=home A=10086:com.android.launcher3 U=0 rootTaskId=1037 visible=true sz=1}",
            // (The Thor's dump repeats the top screen's tasks deeper under the second display.)
            "      * Task{cbb2e2c #1146 type=home U=0 visible=true sz=1}",
            "        * Task{7c30118 #1147 type=home A=10207:com.shelf.launcher U=0 rootTaskId=1146 visible=true sz=1}",
            "      * Task{89a18a9 #1151 type=standard A=10130:dev.eden.eden_emulator U=0 visible=false sz=1}"
        )
        val s = ScreenApps.parse(lines, own)
        assertEquals("dev.eden.eden_emulator", s[0]?.packageName)
        assertFalse(s[0]!!.showing)
        assertNull(s[4])
    }

    @Test fun anAppShowingOnEachScreen() {
        val lines = listOf(
            "Display #4 (activities from top to bottom):",
            "  * Task{2aa15d7 #1160 type=standard A=10301:com.google.android.youtube U=0 visible=true sz=1}",
            "Display #0 (activities from top to bottom):",
            "  * Task{89a18a9 #1151 type=standard A=10130:dev.eden.eden_emulator U=0 visible=true sz=1}",
            "  * Task{a98ac29 #1146 type=home U=0 visible=false sz=1}"
        )
        val s = ScreenApps.parse(lines, own)
        assertEquals("dev.eden.eden_emulator", s[0]?.packageName); assertTrue(s[0]!!.showing)
        assertEquals("com.google.android.youtube", s[4]?.packageName); assertTrue(s[4]!!.showing)
    }
}
