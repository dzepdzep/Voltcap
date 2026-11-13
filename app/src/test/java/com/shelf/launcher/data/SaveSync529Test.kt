package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Update 529 (save sync P0): one test per confirmed finding. Pure JVM - no Drive, no account, no real save. */
class SaveSync529Test {
    private val src = { name: String ->
        listOf("src/main/java/com/shelf/launcher/data/$name", "app/src/main/java/com/shelf/launcher/data/$name").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")
    }
    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    // ---- Findings 3 / 4: change detection ----

    @Test fun sameSizeSameDateRewrittenWithinOneStepIsNotTrusted() {
        // Hashed at t=10_000 a file dated 9_000 (same FAT 2 s step / same second): a second write in that step keeps size and date.
        assertFalse(SaveChangeDetect.trusted(8192, 9_000, 10_000, 8192, 9_000))
    }

    @Test fun aDateClearlyBeforeTheHashIsTrusted() {
        assertTrue(SaveChangeDetect.trusted(8192, 1_000, 10_000, 8192, 1_000))
        // ... and any change of size or date is not.
        assertFalse(SaveChangeDetect.trusted(8192, 1_000, 10_000, 8193, 1_000))
        assertFalse(SaveChangeDetect.trusted(8192, 1_000, 10_000, 8192, 1_001))
    }

    @Test fun aFolderThatReportsNoDateIsAlwaysReadAgain() {
        // SAF providers without COLUMN_LAST_MODIFIED give 0: every later same-size save looked unchanged forever before.
        assertFalse(SaveChangeDetect.trusted(8192, 0, 10_000, 8192, 0))
    }

    @Test fun recordsFromBeforeThisUpdateAreReadOnceAgain() {
        // Older records carry no time of hashing (0).
        assertFalse(SaveChangeDetect.trusted(8192, 1_000, 0, 8192, 1_000))
    }

    @Test fun theWatcherSignatureSeesContentNotOnlyDate() {
        val a = SaveChangeDetect.signature("RetroArch", "gb/Zelda.srm", 8192, 9_000, md5(byteArrayOf(1)))
        val b = SaveChangeDetect.signature("RetroArch", "gb/Zelda.srm", 8192, 9_000, md5(byteArrayOf(2)))
        assertNotEquals(a, b)
    }

    @Test fun cloudSyncUsesTheRuleEverywhere() {
        val s = src("CloudSync.kt")
        assertTrue(s.contains("if (SaveChangeDetect.trusted(it.size, it.modified, it.at, entry.size, entry.modified)) return it.md5"))
        assertFalse(s.contains("if (it.size == entry.size && it.modified == entry.modified) return it.md5"))
        // changedSaves compares CONTENT with the synced base.
        assertTrue(s.contains("val unchanged = base != null && md5 != null && md5.equals(base.md5, ignoreCase = true)"))
        assertTrue(src("SaveObserver.kt").contains("SaveChangeDetect.signature("))
    }

    // ---- Findings 1 / 2: durable per-game queues ----

    @Test fun everyGameHasItsOwnAfterGameQueue() {
        val a = PostGameSyncWorker.workName("rom:/storage/gb/Zelda.gb")
        val b = PostGameSyncWorker.workName("rom:/storage/gba/Metroid.gba")
        assertNotEquals(a, b)
        assertEquals(a, PostGameSyncWorker.workName("rom:/storage/gb/Zelda.gb"))
        assertNotEquals(PostGameSyncWorker.workName(null), a)
        assertNotEquals(SaveNowWorker.workName("rom:/storage/gb/Zelda.gb"), a)
    }

    @Test fun noQueueReplacesAnotherGamesUploadOrDropsAChange() {
        val w = src("SaveWatchWorker.kt")
        val pg = w.substring(w.indexOf("class PostGameSyncWorker"))
        assertTrue(pg.contains(".enqueueUniqueWork(workName(game?.key), ExistingWorkPolicy.APPEND_OR_REPLACE, request)"))
        assertFalse(pg.contains("enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE"))
        val o = src("SaveObserver.kt")
        assertTrue(o.contains(".enqueueUniqueWork(workName(game.key), ExistingWorkPolicy.APPEND_OR_REPLACE, request)"))
        assertFalse(o.contains("ExistingWorkPolicy.KEEP"))
        // A change still unsent when the job ends is retried, then handed to the after-game queue - never ended as success.
        assertTrue(o.contains("unsent && runAttemptCount < MAX_ATTEMPTS -> Result.retry()"))
        assertTrue(o.contains("PostGameSyncWorker.enqueue(app, game)"))
    }

    // ---- Finding 8: backups before any save is replaced ----

    private fun tmp() = kotlin.io.path.createTempDirectory("vc529").toFile()

    @Test fun aBackupIsVerifiedAndDescribed() {
        val base = tmp()
        val bytes = ByteArray(128 * 1024) { (it * 31).toByte() }
        val f = SaveBackups.backup(base, "RetroArch", "gb/Zelda (USA).srm", { bytes.inputStream() }, 1_700_000_000_000, "Flip 2", "test",
            replacedBy = "abc", now = 1_800_000_000_000)
        assertEquals(md5(bytes), md5(f.readBytes()))
        assertEquals("Zelda (USA).srm", f.name.substringBeforeLast('.'))
        // The original name, extension and folder are kept: the copy sits under the same relative path.
        assertTrue(f.parentFile!!.path.endsWith("RetroArch${File.separator}gb"))
        val meta = File(f.parentFile, f.name + SaveBackups.META).readText()
        assertTrue(meta.contains("\"md5\": \"${md5(bytes)}\"") && meta.contains("\"device\": \"Flip 2\"") && meta.contains("\"size\": ${bytes.size}"))
        assertFalse(f.parentFile!!.listFiles()!!.any { it.name.endsWith(".part") })
    }

    @Test fun aBackupThatCannotBeMadeThrowsAndLeavesNothing() {
        val base = tmp()
        // The folder for this save can't be created (a file is in the way).
        File(base, "RetroArch").writeText("not a folder")
        try {
            SaveBackups.backup(base, "RetroArch", "gb/Zelda.srm", { byteArrayOf(1, 2, 3).inputStream() }, 0, "d", "t")
            fail("must throw")
        } catch (e: SaveBackups.BackupFailed) { }
        // A read that breaks halfway: no backup, no partial file.
        val base2 = tmp()
        val broken = object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 10) 7 else throw java.io.IOException("card removed")
        }
        try {
            SaveBackups.backup(base2, "F", "a.sav", { broken }, 0, "d", "t"); fail("must throw")
        } catch (e: SaveBackups.BackupFailed) { }
        assertTrue(File(base2, "F").listFiles().orEmpty().none { it.isFile })
    }

    @Test fun retentionKeepsTenAndEverythingRecent() {
        val base = tmp()
        val day = 24L * 60 * 60 * 1000
        val start = 1_800_000_000_000
        // 14 backups, one per day: the newest 10 stay, of the 4 older only those under 30 days old stay.
        repeat(14) { i ->
            val f = SaveBackups.backup(base, "F", "x.srm", { byteArrayOf(i.toByte()).inputStream() }, 0, "d", "t", now = start + i * day)
            f.setLastModified(start + i * day)
        }
        val dir = File(File(base, "F"), "")
        val left = SaveBackups.revisions(dir, "x.srm")
        assertEquals(14, left.size) // all younger than 30 days at the time each prune ran
        // A much later backup prunes the old extras (older than 30 days), never the newest 10.
        val f = SaveBackups.backup(base, "F", "x.srm", { byteArrayOf(99).inputStream() }, 0, "d", "t", now = start + 60 * day)
        f.setLastModified(start + 60 * day)
        SaveBackups.prune(dir, "x.srm", start + 60 * day)
        assertEquals(SaveBackups.KEEP_NEWEST, SaveBackups.revisions(dir, "x.srm").size)
        // Another file that merely starts with the same name is never touched.
        assertFalse(SaveBackups.isRevisionOf("x.srm2.20261113-010101", "x.srm"))
        assertFalse(SaveBackups.isRevisionOf("x.srm.20261113-010101.json", "x.srm"))
        assertTrue(SaveBackups.isRevisionOf("x.srm.20261113-010101-2", "x.srm"))
    }

    @Test fun downloadsNeverReplaceASaveWithoutTheBackup() {
        val s = src("CloudSync.kt")
        val bb = s.substring(s.indexOf("private fun backupBeforeOverwrite"), s.indexOf("internal fun backupsBase"))
        assertFalse("no longer best effort", bb.contains("runCatching"))
        assertTrue(bb.contains("SaveBackups.backup("))
        // pull backs up BEFORE tree.write, and the conflict choice "use Drive's" backs up and checks Drive's checksum too.
        val pull = s.substring(s.indexOf("private fun pull("), s.indexOf("private class RemoteMoved"))
        assertTrue(pull.indexOf("backupBeforeOverwrite") in 1 until pull.indexOf("tree.write(rel, tmp, modified)"))
        // Multi-file saves and the conflict choices go through pullUnit: SaveUnitTransaction with a verified SaveBackups backup.
        val unit = s.substring(s.indexOf("private fun pullUnit("), s.indexOf("internal fun pickTwin"))
        assertTrue(unit.contains("SaveUnitTransaction.run(") && unit.contains("SaveBackups.backup("))
        val resolve = s.substring(s.indexOf("suspend fun resolveConflicts"), s.indexOf("// ---- Per-game status ----"))
        assertTrue(resolve.contains("pullUnit(context, drive, tree, files, state, folderName, null)"))
        assertTrue("Drive's checksum is required", resolve.contains("if (f.md5 == null) error("))
        // Keeping this device's version: Drive's copy is parked checked, and replaced only if it is still that version.
        assertTrue(resolve.contains("expectRemoteMd5 = now?.md5"))
        // The sync loop sends every multi-file download through pullUnit.
        assertTrue(s.contains("SyncDecision.Action.DOWNLOAD -> (card ?: unit)?.let { unitDownloads.getOrPut(it)"))
    }

    // ---- Upload verification ----

    @Test fun anUploadCountsOnlyWithDrivesChecksumOfTheBytesSent() {
        val s = src("CloudSync.kt")
        assertTrue(s.contains("val got = up.md5 ?: drive.file(up.id)?.md5"))
        assertTrue(s.contains("if (got == null || !got.equals(sent, ignoreCase = true)) error("))
    }

    // ---- Finding 5: before Play ----

    @Test fun playWaitsUnlessTheSaveWasJustConfirmed() {
        val now = 1_800_000_000_000
        assertFalse(PrelaunchCheck.needed(false, null, now))
        assertTrue(PrelaunchCheck.needed(true, null, now))
        val fresh = GameSyncStatus(state = SyncState.SYNCED, lastSuccessAt = now - 5_000, checkedAt = now - 5_000)
        assertFalse(PrelaunchCheck.needed(true, fresh, now))
        // Old check, an upload-only success (no checkedAt), a pending change, an error: check again.
        assertTrue(PrelaunchCheck.needed(true, fresh.copy(checkedAt = now - 120_000), now))
        assertTrue(PrelaunchCheck.needed(true, fresh.copy(checkedAt = 0), now))
        assertTrue(PrelaunchCheck.needed(true, fresh.copy(state = SyncState.PENDING), now))
        assertTrue(PrelaunchCheck.needed(true, fresh.copy(state = SyncState.ERROR), now))
        // A check dated in the future (clock change) is not trusted.
        assertTrue(PrelaunchCheck.needed(true, fresh.copy(checkedAt = now + 60_000), now))
    }

    @Test fun anUnfinishedOrHeldCheckNeverStartsTheGameSilently() {
        assertEquals(PrelaunchCheck.Next.ASK, PrelaunchCheck.next(null))
        assertEquals(PrelaunchCheck.Next.ASK, PrelaunchCheck.next(SyncOutcome(false, "Sync stopped.")))
        assertEquals(PrelaunchCheck.Next.ASK, PrelaunchCheck.next(SyncOutcome(false, "offline", retry = true)))
        assertEquals(PrelaunchCheck.Next.ASK, PrelaunchCheck.next(SyncOutcome(true, "held", heldBack = true)))
        assertEquals(PrelaunchCheck.Next.CONFLICT, PrelaunchCheck.next(SyncOutcome(true, "c", conflicts = 1)))
        assertEquals(PrelaunchCheck.Next.PLAY, PrelaunchCheck.next(SyncOutcome(true, "ok", downloaded = 1)))
    }

    @Test fun aCheckThatFailsOrStopsAsksInsteadOfStartingOrHanging() {
        val vm = File("src/main/java/com/shelf/launcher/LauncherViewModel.kt").takeIf { it.isFile }?.readText()?.replace("\r\n", "\n")
            ?: File("app/src/main/java/com/shelf/launcher/LauncherViewModel.kt").readText().replace("\r\n", "\n")
        val f = vm.substring(vm.indexOf("private fun checkDriveThenLaunch"), vm.indexOf("private var syncNoticeJob"))
        // A failed check becomes a not-ok outcome (asks), a stopped check too; only this wait's own timeout is passed on.
        assertTrue(f.contains("catch (e: Exception) {") && f.contains("SyncOutcome(false, \"Google Drive couldn't be checked"))
        assertTrue(f.contains("if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e"))
        // The game starts only on PLAY.
        assertEquals(1, Regex("launchNow\\(game\\)").findAll(f).count())
    }

    // ---- Mid-game sync (update 529, priority hotfix) ----

    @Test fun theWatcherComesBackWhenTheProcessWasEndedMidGame() {
        val w = src("SaveWatchWorker.kt")
        assertTrue(w.contains("if (SaveObserver.resume(app, game)) {"))
        assertTrue(w.contains("const val WATCH_MS = 60_000L"))
        assertTrue(w.contains("private val STEPS_MIN = longArrayOf(1, 10, 20, 40, 60)"))
        val o = src("SaveObserver.kt")
        assertTrue(o.contains("fun resume(context: Context, g: CloudSync.ScopedGame): Boolean"))
    }

    @Test fun aSaveBeingWrittenIsNeverUploaded() {
        val s = src("CloudSync.kt")
        val push = s.substring(s.indexOf("private fun push("), s.indexOf("private fun backupBeforeOverwrite"))
        // Checked before anything is sent.
        assertTrue(push.indexOf("throw StillWriting(rel)") in 1 until push.indexOf("drive.upload("))
    }

    @Test fun nothingIsAppliedWhileAGameRunsAndPlayersAreToldQuietly() {
        val s = src("CloudSync.kt")
        assertTrue(s.contains("SyncDecision.Action.HELD -> { report.remoteNewer++"))
        assertTrue(s.contains("internal suspend fun probeRemote("))
        val n = src("SyncNotifier.kt")
        assertTrue(n.contains("if (midGame && outcome.ok && outcome.conflicts == 0) return"))
        assertTrue(n.contains("fun remoteNewer(") && n.contains(".setSilent(true)"))
        // "Synced" never stands for a save Drive has a newer version of.
        assertTrue(s.contains("outcome.remoteNewer > 0 -> old.copy(\n                    state = SyncState.PENDING"))
    }

    // ---- Duplicate files on Drive ----

    @Test fun twinsArePickedTheSameWayOnEveryDevice() {
        val a = DriveFile(id = "b2", name = "x.srm", isFolder = false, md5 = "1", modifiedTime = 5, size = 1, device = null)
        val b = DriveFile(id = "a1", name = "x.srm", isFolder = false, md5 = "2", modifiedTime = 5, size = 1, device = null)
        val c = DriveFile(id = "c3", name = "x.srm", isFolder = false, md5 = "3", modifiedTime = 4, size = 1, device = null)
        assertEquals("a1", CloudSync.pickTwin(listOf(a, b, c)).id)
        assertEquals("a1", CloudSync.pickTwin(listOf(c, b, a)).id)
    }
}
