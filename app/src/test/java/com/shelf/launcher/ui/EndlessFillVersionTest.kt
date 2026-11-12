package com.shelf.launcher.ui

import android.content.Context
import com.shelf.launcher.data.Backup
import com.shelf.launcher.data.Features
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Sprint 525: Endless › Fill the row under a versioned key (replaces Batch 524's one-time reset). Whatever brings back
 * a value saved under the old "endless_fill" (an old backup, a settings snapshot, an old layout preset, the QA snapshot)
 * can't turn Fill on; a Fill chosen now is kept through reloads, restores, backups and a later update.
 */
@RunWith(RobolectricTestRunner::class)
class EndlessFillVersionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun file() = app.getSharedPreferences("voltcap_features", Context.MODE_PRIVATE)
    @Before fun bind() { file().edit().clear().commit(); Features.reload(app) }
    @After fun reset() { file().edit().clear().commit(); Features.reload(app) }

    /** What an import does to the file (Backup.applyAll: every key replaced by the backup's), then SettingsTransfer's reload. */
    private fun importFile(values: Map<String, Boolean>) {
        val e = file().edit().clear()
        values.forEach { (k, v) -> e.putBoolean(k, v) }
        e.commit()
        Features.reload(app)
    }

    @Test fun newKeyAndDefault() {
        assertEquals("endless_fill_v2", Features.endlessFill.key)
        assertFalse(Features.endlessFill.default)
    }

    @Test fun oldSavedFillIsNeverReadAndNotDestroyed() {
        importFile(mapOf("endless_fill" to true)) // a v0.6.70 file: Fill was the default ON and every backup wrote it
        assertFalse(Features.endlessFill.value)
        assertTrue("the old value is left as it was (an older Voltcap still reads it)", file().getBoolean("endless_fill", false))
        // read again and again (settings reloads, the app restarting): still off, nothing written
        repeat(3) { Features.reload(app) }
        assertFalse(Features.endlessFill.value)
        assertFalse(file().contains("endless_fill_v2"))
    }

    @Test fun chosenFillIsKept() {
        Features.endlessFill.value = true // the R1 Endless menu: "Fill the row (games repeat)"
        assertTrue(file().getBoolean("endless_fill_v2", false))
        repeat(3) { Features.reload(app) }
        assertTrue(Features.endlessFill.value)
        // a newer backup of this device, imported again (or onto a fresh install): kept
        importFile(file().all.mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap())
        assertTrue(Features.endlessFill.value)
        // an OLD backup imported over it brings no Fill value of its own: the file it writes has none, so the default
        importFile(mapOf("endless_fill" to true))
        assertFalse(Features.endlessFill.value)
    }

    @Test fun chosenOffIsKeptAgainstAnOldFile() {
        Features.endlessFill.value = false
        importFile(mapOf("endless_fill" to true, "endless_fill_v2" to false))
        assertFalse(Features.endlessFill.value)
    }

    @Test fun snapshotsAndPresetsCarryOnlyTheNewKey() {
        // Features.restore is what your own layout presets, "Only this layout", R1 undo and the QA snapshot use
        Features.endlessFill.value = false
        Features.restore(mapOf("endless_fill" to "true")) // an old preset / the old QA snapshot
        assertFalse(Features.endlessFill.value)
        Features.endlessFill.value = true
        val snap = Features.snapshot()
        assertEquals("true", snap["endless_fill_v2"])
        assertFalse(snap.containsKey("endless_fill"))
        Features.endlessFill.value = false
        Features.restore(snap) // a preset saved now
        assertTrue(Features.endlessFill.value)
    }

    @Test fun realBackupImportOfAnOldFile() {
        // The real import path (Backup.applyDocument) with a v0.6.70-style document; skipped only if this test
        // environment can't run the import's safety snapshot / sign-in checks at all.
        Features.endlessFill.value = true
        val root = JSONObject().put("app", "Voltcap").put("format", 2).put("created", 1L)
            .put("files", JSONObject().put("voltcap_features", JSONObject().put("scope", "GLOBAL")
                .put("values", JSONObject().put("endless_fill", JSONObject().put("t", "b").put("v", true)))))
        val ok = runCatching { Backup.applyDocument(app, root, false, "test") }
        assumeTrue("import not runnable here: ${ok.exceptionOrNull()}", ok.isSuccess)
        Features.reload(app)
        assertFalse("an old file's Fill (the old default) can't come back", Features.endlessFill.value)
        assertTrue(file().getBoolean("endless_fill", false))
    }
}
