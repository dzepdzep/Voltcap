package com.shelf.launcher.ui

import android.content.Context
import com.shelf.launcher.data.Features
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Batch 524: a saved Fill the row is turned off once; a Fill picked after that is kept. */
@RunWith(RobolectricTestRunner::class)
class EndlessFillMigrationTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("m524test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test fun savedFillTurnedOffOnce() {
        val p = prefs()
        p.edit().putBoolean("endless_fill", true).commit()
        Features.endlessFill.state.value = true
        Features.migrate524(p)
        assertFalse(p.getBoolean("endless_fill", true))
        assertFalse(Features.endlessFill.value)
        assertTrue(p.getBoolean("m524_endless_fill_done", false))
        // picked again afterwards: kept
        p.edit().putBoolean("endless_fill", true).commit()
        Features.endlessFill.state.value = true
        Features.migrate524(p)
        assertTrue(p.getBoolean("endless_fill", false))
        assertTrue(Features.endlessFill.value)
        Features.endlessFill.state.value = false
    }

    @Test fun nothingSavedStaysDefault() {
        val p = prefs()
        Features.migrate524(p)
        assertFalse(p.contains("endless_fill"))
        assertTrue(p.getBoolean("m524_endless_fill_done", false))
    }
}
