package com.shelf.launcher.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.shelf.launcher.device.tune.TuneStore
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 373: the Thor crash (0.6.54, Case Stacks) "Reading a state that was created after the snapshot was taken" in
 * rememberGamePerf / TuneStore.edits, and the 7 s main-thread freeze in Tuner.supported -> RootShell.exec.
 */
class Tuner373Test {
    private fun src(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/", "app/src/main/java/com/shelf/launcher/")
        return roots.map { File(it + name) }.first { it.exists() }.readText()
    }

    /** The mechanism of the crash: a snapshot state made after a snapshot was taken cannot be read inside it. */
    @Test fun composeStateCreatedAfterASnapshotThrowsWhenReadInsideIt() {
        val snap = Snapshot.takeSnapshot()
        try {
            val late = mutableIntStateOf(0)
            var threw = false
            try { snap.enter { late.intValue } } catch (e: IllegalStateException) { threw = true }
            assertTrue("this is the crash", threw)
        } finally { snap.dispose() }
    }

    @Test fun editCounterIsAPlainFlowReadableFromAnySnapshotOrThread() {
        assertTrue(TuneStore.edits is StateFlow<*>)
        val snap = Snapshot.takeSnapshot()
        try {
            val before = TuneStore.edits.value
            val t = Thread { repeat(50) { TuneStore.bumpEdits() } }
            t.start(); t.join()
            assertEquals(before + 50, snap.enter { TuneStore.edits.value })
        } finally { snap.dispose() }
    }

    @Test fun noSnapshotStateInTheTuneStoreAndNoRootQueryInComposition() {
        val model = src("device/tune/TuneModel.kt")
        val companion = model.substring(model.indexOf("companion object"), model.indexOf("private fun migrateUnderclockOnce"))
        assertFalse(companion.contains("mutableIntStateOf") || companion.contains("mutableStateOf"))
        val popup = src("ui/GamePopupStatus.kt")
        val perf = popup.substring(popup.indexOf("fun rememberGamePerf("), popup.indexOf("internal fun perfRows("))
        assertFalse("blocking root probe in composition", perf.contains("Tuner.supported)") || perf.contains("Tuner.supported "))
        assertTrue(perf.contains("Tuner.supportedState.collectAsState()") && perf.contains("Tuner.probeSupported()"))
        assertTrue(perf.contains("TuneStore.edits.collectAsState()"))
        assertFalse(src("ui/GameDetailsOverlay.kt").contains("Tuner.supported\n"))
        val tuner = src("device/tune/Tuner.kt")
        val probe = tuner.substring(tuner.indexOf("suspend fun probeSupported("))
        assertTrue(probe.take(900).contains("Dispatchers.IO"))
        // QuickSettings' produceState body runs on the main thread: the probe sits inside withContext(IO).
        val qs = src("ui/QuickSettings.kt")
        val label = qs.substring(qs.indexOf("val profileLabel by produceState"), qs.indexOf("// Media volume"))
        assertTrue(label.indexOf("withContext(Dispatchers.IO)") < label.indexOf("Tuner.supported"))
    }
}
