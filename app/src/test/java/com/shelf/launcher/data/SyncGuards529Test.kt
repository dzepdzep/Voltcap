package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Update 529c (save sync P0, the risks SAVE_SYNC_529.md section 4 left open): the pure rules in SyncGuards.kt and SaveBackups' restore,
 * plus source checks that CloudSync applies them. Each rule's test fails on the code before 529c (the rule did not exist).
 */
class SyncGuards529Test {
    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }
    private fun r(id: String, md5: String?) = UploadRace.Revision(id, md5)
    private val min = 60_000L

    // ---- risk 2: the last-moment upload race ----

    @Test fun aRevisionBetweenTheExpectedOneAndOursWasReplacedUnseen() {
        val lost = UploadRace.replacedUnseen(listOf(r("1", "a"), r("2", "c"), r("3", "b")), expectMd5 = "a", sentMd5 = "b")
        assertEquals(listOf("2"), lost!!.map { it.id })
    }

    @Test fun noRaceWhenOursFollowsTheExpectedOneDirectly() {
        assertEquals(emptyList<UploadRace.Revision>(), UploadRace.replacedUnseen(listOf(r("0", "x"), r("1", "a"), r("2", "b")), "a", "b"))
    }

    @Test fun aRevisionWithTheSameBytesLosesNothing() {
        assertEquals(emptyList<UploadRace.Revision>(), UploadRace.replacedUnseen(listOf(r("1", "a"), r("2", "B"), r("3", "b")), "a", "b"))
    }

    @Test fun unknownWhenTheListDoesNotShowBothVersions() {
        assertNull(UploadRace.replacedUnseen(listOf(r("1", "c"), r("2", "b")), "a", "b")) // expected pruned
        assertNull(UploadRace.replacedUnseen(listOf(r("1", "a"), r("2", "c")), "a", "b")) // ours not listed
    }

    @Test fun laterUploadsAfterOursAreNotOurs() {
        // Someone uploaded after us: that is a normal later change, not something we replaced.
        val lost = UploadRace.replacedUnseen(listOf(r("1", "a"), r("2", "c"), r("3", "b"), r("4", "d")), "a", "b")
        assertEquals(listOf("2"), lost!!.map { it.id })
    }

    // ---- risk 3: multi-file saves half-sent on Drive ----

    @Test fun theManifestRoundTripsAndIsNeverASaveName() {
        val files = mapOf("PSP/SAVEDATA/X/DATA.BIN" to "AB12", "PSP/SAVEDATA/X/PARAM SFO" to "cd34")
        val text = UnitManifest.encode("PSP/SAVEDATA/X", files)
        assertEquals(mapOf("PSP/SAVEDATA/X/DATA.BIN" to "ab12", "PSP/SAVEDATA/X/PARAM SFO" to "cd34"), UnitManifest.decode(text))
        assertEquals(text, UnitManifest.encode("PSP/SAVEDATA/X", files.toList().reversed().toMap())) // stable bytes
        assertTrue(UnitManifest.isManifest(UnitManifest.nameFor("PSP/SAVEDATA/X")))
        assertFalse(UnitManifest.nameFor("PSP/SAVEDATA/X").contains('/'))
        assertNull(UnitManifest.decode("not a manifest"))
    }

    @Test fun aSaveIsCompleteOnlyWhenEveryFileMatchesItsManifest() {
        val m = mapOf("a" to "1", "b" to "2")
        fun d(vararg p: Pair<String, String?>, marked: Boolean = true) = p.associate { (k, v) -> k to UnitManifest.OnDrive(v, marked) }
        assertEquals(UnitManifest.Verdict.COMPLETE, UnitManifest.check(m, d("a" to "1", "b" to "2")))
        assertEquals(UnitManifest.Verdict.INCOMPLETE, UnitManifest.check(m, d("a" to "9", "b" to "2"))) // one new, one old: in progress
        assertEquals(UnitManifest.Verdict.INCOMPLETE, UnitManifest.check(m, d("a" to "1"))) // a listed file is missing
        assertEquals(UnitManifest.Verdict.NO_MANIFEST, UnitManifest.check(null, d("a" to "9", "b" to "2")))
        // Only an older Voltcap (no mark) changed it since: the manifest is stale, the old behaviour applies (never stuck).
        assertEquals(UnitManifest.Verdict.NO_MANIFEST, UnitManifest.check(m, d("a" to "9", "b" to "2", marked = false)))
    }

    @Test fun aHeldSaveIsHandedToThePlayerOnlyAfterADayOnThisDevicesOwnClock() {
        val now = 1_000_000_000L
        assertFalse(UnitManifest.heldTooLong(now - 60 * min, now))
        assertTrue(UnitManifest.heldTooLong(now - 25 * 60 * min, now))
        assertFalse("a start in the future is ignored", UnitManifest.heldTooLong(now + min, now))
        assertFalse(UnitManifest.heldTooLong(0L, now))
    }

    // ---- risk 4: "Newest" and two clocks ----

    @Test fun newestCorrectsBothClocksToGooglesBeforeComparing() {
        val now = 10_000 * min
        // This handheld's clock runs 20 min fast: its save "at 9 990 min" was really at 9 970 min, before Drive's at 9 980.
        assertEquals(ConflictClock.Pick.DRIVE, ConflictClock.newest(now - 10 * min, 20 * min, now - 20 * min, 0L, now, 20 * min))
        // The raw dates alone would have said "this device" - the old rule (localModified >= remoteModified).
        assertTrue(now - 10 * min >= now - 20 * min)
    }

    @Test fun newestNeverGuessesWhenTheDatesAreClose() {
        val now = 10_000 * min
        assertEquals(ConflictClock.Pick.UNSURE, ConflictClock.newest(now - 10 * min, 0L, now - 11 * min, 0L, now, 0L))
        // Unknown clocks (older uploads): a wider margin.
        assertEquals(ConflictClock.Pick.UNSURE, ConflictClock.newest(now - 10 * min, null, now - 20 * min, null, now, null))
        assertEquals(ConflictClock.Pick.LOCAL, ConflictClock.newest(now - 10 * min, null, now - 60 * min, null, now, null))
        // A date in the future, or none, can't be trusted.
        assertEquals(ConflictClock.Pick.UNSURE, ConflictClock.newest(now + 30 * min, 0L, now - 60 * min, 0L, now, 0L))
        assertEquals(ConflictClock.Pick.UNSURE, ConflictClock.newest(0L, 0L, now, 0L, now, 0L))
    }

    // ---- pending uploads across a restart ----

    @Test fun aSessionFromBeforeTheRestartIsNotRunning() {
        val now = 50_000 * min
        assertTrue(BootSession.startedBeforeBoot(sessionStart = now - 60 * min, now = now, elapsedSinceBoot = 10 * min))
        assertFalse(BootSession.startedBeforeBoot(sessionStart = now - 5 * min, now = now, elapsedSinceBoot = 10 * min))
        assertFalse(BootSession.startedBeforeBoot(0L, now, 10 * min))
    }

    // ---- risk 5: restoring an earlier version ----

    private fun tmp() = File(System.getProperty("java.io.tmpdir"), "voltcap-529c-${System.nanoTime()}").apply { mkdirs() }

    private fun setUp(): Triple<File, SaveBackups.Version, File> {
        val base = tmp()
        val live = File(tmp(), "game.srm").apply { writeBytes(ByteArray(4096) { 7 }) }
        // A kept version of the save (as made before Drive's replaced it).
        val old = ByteArray(4096) { 3 }
        SaveBackups.backup(base, "RetroArch saves", "Gambatte/game.srm", { old.inputStream() }, 1L, "Flip 2", "replaced by Google Drive's version")
        val v = SaveBackups.versions(base, "RetroArch saves", "Gambatte/game.srm").single()
        return Triple(base, v, live)
    }

    @Test fun earlierVersionsAreListedWithTheirRecord() {
        val (_, v, _) = setUp()
        assertEquals("Flip 2", v.meta!!.device)
        assertEquals(md5(ByteArray(4096) { 3 }), v.md5)
        assertEquals("Gambatte/game.srm", v.meta!!.path)
    }

    @Test fun restoreKeepsTheCurrentSaveFirstAndReadsTheResultBack() {
        val (base, v, live) = setUp()
        val now = System.currentTimeMillis() + 5_000
        val got = SaveBackups.restore(base, "RetroArch saves", "Gambatte/game.srm", v, { live.inputStream() }, live.lastModified(), "Flip 2",
            write = { f -> live.writeBytes(f.readBytes()) }, readBack = { md5(live.readBytes()) }, now = now)
        assertEquals(md5(ByteArray(4096) { 3 }), got)
        assertEquals(md5(ByteArray(4096) { 3 }), md5(live.readBytes()))
        // The save that was live is now an earlier version itself.
        assertTrue(SaveBackups.versions(base, "RetroArch saves", "Gambatte/game.srm").any { it.md5 == md5(ByteArray(4096) { 7 }) })
    }

    @Test fun aDamagedVersionIsNeverRestored() {
        val (base, v, live) = setUp()
        v.file.writeBytes(ByteArray(4096) { 5 }) // the copy changed after it was made
        try {
            SaveBackups.restore(base, "RetroArch saves", "Gambatte/game.srm", v, { live.inputStream() }, live.lastModified(), "Flip 2",
                write = { fail("nothing may be written") }, readBack = { null })
            fail("restored a damaged copy")
        } catch (e: SaveBackups.RestoreFailed) { /* expected */ }
        assertEquals(md5(ByteArray(4096) { 7 }), md5(live.readBytes()))
    }

    @Test fun aRestoreThatDoesNotReadBackPutsTheSaveBack() {
        val (base, v, live) = setUp()
        var writes = 0
        try {
            SaveBackups.restore(base, "RetroArch saves", "Gambatte/game.srm", v, { live.inputStream() }, live.lastModified(), "Flip 2",
                write = { f -> writes++; if (writes == 1) live.writeBytes(f.readBytes().copyOf(100)) else live.writeBytes(f.readBytes()) },
                readBack = { md5(live.readBytes()) }, now = System.currentTimeMillis() + 7_000)
            fail("a bad restore was accepted")
        } catch (e: SaveBackups.RestoreFailed) { /* expected */ }
        assertEquals("the save it had is back", md5(ByteArray(4096) { 7 }), md5(live.readBytes()))
    }

    // ---- wiring (source checks) ----

    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/data/$name", "app/src/main/java/com/shelf/launcher/data/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun cloudSyncAppliesTheGuards() {
        val c = src("CloudSync.kt")
        assertTrue(c.contains("keepRacedRevisions(context, drive, up.id, rel, folderName, expectRemoteMd5, sent)"))
        assertTrue(c.contains("UnitManifest.isManifest(it.name)"))
        assertTrue(c.contains("UnitGate.HOLD -> { report.unitsHeld++; return@forEach }"))
        assertTrue(c.contains("writeUnitManifest(drive, remoteDir, unit, state, ::unitKeyOf, manifests[UnitManifest.nameFor(unit)])"))
        assertTrue(c.contains("ConflictClock.Pick.UNSURE -> { unsure++; return@forEach }"))
        assertTrue(c.contains("conflicts = it.conflicts.filterNot { c -> c in settled }"))
        assertTrue(src("PrelaunchCheck.kt").contains("outcome.unitsHeld > 0 -> Next.ASK"))
        assertTrue(src("SaveWatchWorker.kt").contains("BootSession.startedBeforeBoot(started, now"))
        assertTrue(src("PlaySession.kt").contains("BootSession.startedBeforeBoot(since, now"))
    }

    @Test fun aHeldSaveDoesNotLetPlayStartSilently() {
        assertEquals(PrelaunchCheck.Next.ASK, PrelaunchCheck.next(SyncOutcome(true, "x", unitsHeld = 1)))
        assertEquals(PrelaunchCheck.Next.PLAY, PrelaunchCheck.next(SyncOutcome(true, "x")))
    }

    /** The real-Drive test may only ever sync the QA app's own synthetic folders - one real folder in the list stops it. */
    @Test fun theQaDriveTestStaysInsideTheQaAppsFolder() {
        val root = "/storage/emulated/0/Android/data/com.shelf.launcher.qa/files"
        fun s(vararg paths: String, data: Boolean = false, tree: String? = null) =
            CloudSyncSettings(syncSaves = true, syncLauncherData = data, folders = paths.map { SyncFolder("F", it, treeUri = tree) })
        assertTrue(QaDriveTest.confined(s("$root/qa_drive_saves/A"), root))
        assertFalse(QaDriveTest.confined(s("$root/qa_drive_saves/A", "/storage/emulated/0/RetroArch/saves"), root))
        assertFalse(QaDriveTest.confined(s("$root/../../com.retroarch/files"), root))
        assertFalse(QaDriveTest.confined(s("$root-evil/x"), root))
        assertFalse("never Voltcap's own data", QaDriveTest.confined(s("$root/qa_drive_saves/A", data = true), root))
        assertFalse(QaDriveTest.confined(s("$root/x", tree = "content://dolphin"), root))
        assertFalse(QaDriveTest.confined(s(), root))
    }

    /**
     * Rule 11 on the save screens (13 Nov, 529c): a game's name, a save's file name or place, a Drive problem or a sync line is never
     * cut with "..." - two saves that differ only at the end of the name (region, revision, hack) must stay told apart.
     */
    @Test fun theSaveScreensNeverCutANameOrAPlace() {
        fun ui(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
            .map(::File).first { it.exists() }.readText()
        for (f in listOf("SaveExplorer.kt", "SaveSyncUi.kt", "SyncPrompts.kt")) {
            val t = ui(f)
            assertFalse("$f cuts text", t.contains("TextOverflow.Ellipsis"))
        }
        assertFalse("a save row has a fixed height", ui("SaveExplorer.kt").contains("Row(Modifier.fillMaxWidth().height(46.dp)"))
    }
}
