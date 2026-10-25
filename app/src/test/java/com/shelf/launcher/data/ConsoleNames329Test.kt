package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Update 329: official console names on the screens unless Short console names is on. */
class ConsoleNames329Test {
    @Test fun officialByDefaultShortWhenAsked() {
        val before = Features.shortConsoleNames.value
        try {
            Features.shortConsoleNames.value = false
            assertEquals("Game Boy Advance", ConsoleNames.label("GBA"))
            assertEquals("PlayStation 2", ConsoleNames.label("PS2"))
            Features.shortConsoleNames.value = true
            assertEquals("GBA", ConsoleNames.label("GBA"))
            assertEquals("PS2", ConsoleNames.label("PS2"))
            assertEquals("Mega Drive", ConsoleNames.label("MegaDrive"))
            // What's printed on media stays official either way.
            assertEquals("Game Boy Advance", ConsoleNames.display("GBA"))
        } finally {
            Features.shortConsoleNames.value = before
        }
    }
}
