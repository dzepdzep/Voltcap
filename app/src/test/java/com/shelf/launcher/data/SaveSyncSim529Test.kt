package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Update 529 (save sync P0): two (and three) handhelds sharing one fake Google Drive, run through the adversarial cases of
 * the reliability brief - on the JVM, with synthetic saves only (no real saves, no real Drive, no device).
 *
 * What is real here: the decision table ([SyncDecision.decide]), the twin pick ([CloudSync.pickTwin]), the all-or-none
 * multi-file write ([SaveUnitTransaction.run]) and the verified backups ([SaveBackups.backup], on real temporary files).
 * What is modelled (mirroring [CloudSync.syncFolder] / push / resolveConflicts step by step): the listing, the upload with its
 * "Drive still has the version we decided on" check and Drive's checksum check, the multi-file unit detection, the conflict
 * choices. Drive itself is an in-memory list of files with ids (twins allowed), able to fail downloads, corrupt them or let
 * another device upload in the middle of a sync.
 *
 * The invariant checked after EVERY step: the latest save a player made on any device (its bytes) still exists somewhere -
 * on a device, on Drive, in a verified backup or in a parked conflict copy. A sync may never be what makes progress vanish.
 */
class SaveSyncSim529Test {

    // ---------- the fake world ----------

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }
    private fun md5(f: File) = md5(f.readBytes())

    private var clock = 1_800_000_000_000L
    private fun tick() = (clock + 1_000).also { clock = it }

    private inner class DFile(val id: String, val name: String, var bytes: ByteArray, var modified: Long, var device: String) {
        fun meta() = DriveFile(id = id, name = name, isFolder = false, md5 = md5(bytes), modifiedTime = modified, size = bytes.size.toLong(), device = device)
    }

    private inner class Drive {
        val files = mutableListOf<DFile>()
        private var next = 0
        /** Downloads of these names fail (offline / interrupted). */
        var failDownloads = setOf<String>()
        /** Downloads of these names arrive mangled (Drive still reports the right checksum). */
        var corruptDownloads = setOf<String>()
        /** Runs once right before the next upload of a name - another handheld syncing in the middle. */
        val beforeUpload = mutableMapOf<String, () -> Unit>()
        /** Runs once when this device's push of a name starts - another handheld uploaded after this one listed Drive. */
        val beforePush = mutableMapOf<String, () -> Unit>()

        /** Every version Drive ever held. */
        val everHeld = mutableSetOf<String>()
        fun file(id: String) = files.firstOrNull { it.id == id }
        fun download(id: String, to: File) {
            val f = file(id) ?: error("gone")
            if (f.name in failDownloads) error("offline")
            to.writeBytes(if (f.name in corruptDownloads) f.bytes.copyOf(f.bytes.size / 2) else f.bytes)
        }
        fun upload(name: String, bytes: ByteArray, existingId: String?, modified: Long, device: String): DFile {
            beforeUpload.remove(name)?.invoke()
            val f = existingId?.let { file(it) }
            everHeld += md5(bytes)
            return if (f != null) f.also { it.bytes = bytes.copyOf(); it.modified = modified; it.device = device }
            else DFile("id${next++}", name, bytes.copyOf(), modified, device).also { files += it }
        }
    }

    private data class Base(val md5: String, val remoteId: String)

    private inner class Device(val name: String, root: File) {
        val local = sortedMapOf<String, ByteArray>()
        val modified = mutableMapOf<String, Long>()
        val base = mutableMapOf<String, Base>()
        val backupsDir = File(root, "backups-$name").apply { mkdirs() }
        val parked = mutableListOf<ByteArray>()
        val conflicts = mutableListOf<String>()
        /** Writes into the save folder that fail (storage full / card removed): the n-th write from now. */
        var failWriteAt = -1
        /** The storage's date step: 2000 for a FAT / exFAT card (dates in 2 s steps). */
        var dateStep = 1L
        /** CloudSync's remembered hashes (size, date, md5, when hashed), reused only under [SaveChangeDetect.trusted]. */
        val hashes = mutableMapOf<String, LongArray>()
        val hashMd5 = mutableMapOf<String, String>()
        fun write(rel: String, bytes: ByteArray, mod: Long) {
            if (failWriteAt == 0) { failWriteAt = -1; error("storage full") }
            if (failWriteAt > 0) failWriteAt--
            local[rel] = bytes.copyOf(); modified[rel] = mod / dateStep * dateStep
        }
        /** CloudSync.localHash. */
        fun localHash(rel: String): String? {
            val bytes = local[rel] ?: return null
            val mod = modified[rel] ?: 0L
            val h = hashes[rel]
            if (h != null && SaveChangeDetect.trusted(h[0], h[1], h[2], bytes.size.toLong(), mod)) return hashMd5.getValue(rel)
            return md5(bytes).also { hashes[rel] = longArrayOf(bytes.size.toLong(), mod, clock); hashMd5[rel] = it }
        }
        fun backupBytes(): List<ByteArray> = backupsDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(SaveBackups.META) && !it.name.endsWith(".part") }.map { it.readBytes() }.toList()
        private val seen = mutableMapOf<String, String>()
        /** md5 of every backup (a backup never changes once made, so each is read once). */
        fun backupSums(): Collection<String> {
            val present = backupsDir.walkTopDown().filter { it.isFile && !it.name.endsWith(SaveBackups.META) && !it.name.endsWith(".part") }.toList()
            present.forEach { f -> seen.getOrPut(f.path) { md5(f.readBytes()) } }
            seen.keys.retainAll(present.map { it.path }.toSet())
            return seen.values
        }
    }

    private val tmpRoot = File(System.getProperty("java.io.tmpdir"), "voltcap-sim-${System.nanoTime()}").apply { mkdirs() }

    /** The player saved in the emulator: the newest progress of [d] for [rel]. */
    private val latest = mutableMapOf<Pair<String, String>, String>()
    private fun play(d: Device, rel: String, bytes: ByteArray) {
        d.write(rel, bytes, tick())
        latest[d.name to rel] = md5(bytes)
    }

    /** CloudSync.syncFolder, step by step, for one save folder. */
    private fun sync(d: Device, drive: Drive, unitOf: (String) -> String? = { null }) {
        val remote = drive.files.groupBy { it.name }.mapValues { (_, same) -> CloudSync.pickTwin(same.map { it.meta() }) }
        val all = (d.local.keys + remote.keys).sorted()
        val lh = { rel: String -> d.localHash(rel) }
        // mixedSaveUnits
        val here = mutableSetOf<String>(); val there = mutableSetOf<String>()
        for (rel in all) {
            val u = unitOf(rel) ?: continue
            val l = lh(rel) ?: continue
            val r = remote[rel] ?: continue
            if (l == r.md5) continue
            val b = d.base[rel]
            if (b == null || l != b.md5) here += u
            if (b == null || r.md5 != b.md5) there += u
        }
        val mixed = here intersect there
        val unitDownloads = linkedMapOf<String, MutableList<String>>()
        for (rel in all) {
            val l = lh(rel); val r = remote[rel]; val b = d.base[rel]; val u = unitOf(rel)
            val action = SyncDecision.decide(SyncDecision.Input(l, r?.md5, b?.md5, r?.id, b?.remoteId, inMixedUnit = u != null && u in mixed))
            when (action) {
                SyncDecision.Action.NOTHING -> Unit
                SyncDecision.Action.IN_SYNC -> d.base[rel] = Base(l!!, r!!.id)
                SyncDecision.Action.UPLOAD -> push(d, drive, rel, r?.id, r?.md5)
                SyncDecision.Action.DOWNLOAD -> unitDownloads.getOrPut(u ?: "file:$rel") { mutableListOf() } += rel
                SyncDecision.Action.CONFLICT -> d.conflicts += rel
            }
            check()
        }
        unitDownloads.values.forEach { rels ->
            try { pullUnit(d, drive, rels.map { it to remote.getValue(it) }) } catch (e: Exception) { /* reported as not synced */ }
            check()
        }
    }

    /** CloudSync.push: snapshot, "Drive still has what we decided on", upload, Drive's checksum must equal what was sent. */
    private fun push(d: Device, drive: Drive, rel: String, existingId: String?, expect: String?) {
        drive.beforePush.remove(rel)?.invoke()
        val snap = d.local.getValue(rel).copyOf()
        val sent = md5(snap)
        if (existingId != null && expect != null) {
            val now = drive.file(existingId)
            if (now != null && md5(now.bytes) != expect) { d.conflicts += rel; return } // RemoteMoved
        }
        // (The race hook runs inside upload: another device that uploads "meanwhile" without listing first is the twin case.)
        val up = drive.upload(rel, snap, existingId, d.modified[rel] ?: tick(), d.name)
        if (md5(up.bytes) == sent) d.base[rel] = Base(sent, up.id)
    }

    private fun pullUnit(d: Device, drive: Drive, files: List<Pair<String, DriveFile>>, byChoice: Boolean = false) {
        val sums = SaveUnitTransaction.run(
            files.map { (rel, r) -> SaveUnitTransaction.Item(rel, r.md5, r.modifiedTime) },
            fetch = { item -> File.createTempFile("pull", ".part", tmpRoot).also { drive.download(files.toMap().getValue(item.rel).id, it) } },
            md5 = { md5(it) },
            backup = { item ->
                d.local[item.rel]?.let { bytes ->
                    SaveBackups.backup(d.backupsDir, "F", item.rel, { bytes.inputStream() }, d.modified[item.rel] ?: 0, d.name, "test", item.expectMd5, tick()) to (d.modified[item.rel] ?: 0L)
                }
            },
            restore = { item, (copy, mod) -> d.local[item.rel] = copy.readBytes(); d.modified[item.rel] = mod },
            write = { item, f ->
                // Never silently: a download may replace a save that Drive already had, never progress that was never uploaded.
                d.local[item.rel]?.let { old ->
                    val m = md5(old)
                    if (!byChoice && m == latest[d.name to item.rel] && m !in drive.everHeld) fail("${d.name}: unsent progress of ${item.rel} replaced by a download")
                }
                d.write(item.rel, f.readBytes(), item.modified)
            }
        ) ?: return
        files.forEach { (rel, r) ->
            d.base[rel] = Base(sums.getValue(rel), r.id)
            d.hashes[rel] = longArrayOf(d.local.getValue(rel).size.toLong(), d.modified.getValue(rel), clock); d.hashMd5[rel] = sums.getValue(rel)
        }
    }

    /** CloudSync.resolveConflicts for [d]'s open conflicts. */
    private fun resolve(d: Device, drive: Drive, keepLocal: Boolean, unitOf: (String) -> String? = { null }) {
        val remote = drive.files.groupBy { it.name }.mapValues { (_, same) -> CloudSync.pickTwin(same.map { it.meta() }) }
        val fromDrive = linkedMapOf<String, MutableList<String>>()
        val open = d.conflicts.distinct()
        d.conflicts.clear()
        for (rel in open) {
            val r = remote[rel] ?: continue
            val entry = d.local[rel]
            if (!keepLocal || entry == null) {
                if (entry != null) d.parked += entry.copyOf()
                fromDrive.getOrPut(unitOf(rel) ?: "file:$rel") { mutableListOf() } += rel
            } else {
                val now = drive.file(r.id)!!
                d.parked += now.bytes.copyOf()
                push(d, drive, rel, r.id, md5(now.bytes))
            }
        }
        // (A choice that can't be carried out stays open, as in resolveConflicts.)
        fromDrive.values.forEach { rels -> try { pullUnit(d, drive, rels.map { it to remote.getValue(it) }, byChoice = true) } catch (e: Exception) { d.conflicts += rels } }
        check()
    }

    private lateinit var devices: List<Device>
    private lateinit var drive: Drive

    /** The invariant: every device's latest progress still exists somewhere. */
    private fun check() {
        val everywhere = mutableSetOf<String>()
        devices.forEach { d -> d.local.values.forEach { everywhere += md5(it) }; everywhere += d.backupSums(); d.parked.forEach { everywhere += md5(it) } }
        drive.files.forEach { everywhere += md5(it.bytes) }
        latest.forEach { (k, v) -> if (v !in everywhere) fail("Progress of ${k.first} for ${k.second} ($v) is gone") }
    }

    private var worlds = 0
    private fun world(vararg names: String): List<Device> {
        drive = Drive()
        // (A fresh folder per world: backups of an earlier run never count for this one.)
        val root = File(tmpRoot, "w${worlds++}").apply { mkdirs() }
        devices = names.map { Device(it, root) }
        return devices
    }

    private fun bytes(tag: String, size: Int = 8192): ByteArray {
        val seed = MessageDigest.getInstance("MD5").digest(tag.toByteArray())
        return ByteArray(size) { i -> (seed[i % 16] + i / 16).toByte() }
    }

    // ---------- scenarios ----------

    @Test fun handOverBetweenTwoHandhelds() {
        val (flip, konkr) = world("Flip2", "KONKR")
        play(flip, "gb/Zelda (USA).srm", bytes("flip-1"))
        sync(flip, drive); sync(konkr, drive)
        assertEquals(md5(bytes("flip-1")), md5(konkr.local.getValue("gb/Zelda (USA).srm")))
        play(konkr, "gb/Zelda (USA).srm", bytes("konkr-2"))
        sync(konkr, drive); sync(flip, drive)
        assertEquals(md5(bytes("konkr-2")), md5(flip.local.getValue("gb/Zelda (USA).srm")))
        // The Flip's older save was backed up (verified) before it was replaced.
        assertTrue(flip.backupBytes().any { md5(it) == md5(bytes("flip-1")) })
        assertTrue(flip.conflicts.isEmpty() && konkr.conflicts.isEmpty())
    }

    @Test fun bothPlayedOfflineIsAConflictAndNothingIsReplaced() {
        val (flip, konkr) = world("Flip2", "KONKR")
        play(flip, "gb/Zelda.srm", bytes("v1")); sync(flip, drive); sync(konkr, drive)
        play(flip, "gb/Zelda.srm", bytes("flip-offline")); play(konkr, "gb/Zelda.srm", bytes("konkr-offline"))
        sync(flip, drive); sync(konkr, drive)
        assertEquals(listOf("gb/Zelda.srm"), konkr.conflicts)
        assertEquals(md5(bytes("konkr-offline")), md5(konkr.local.getValue("gb/Zelda.srm")))
        assertEquals(md5(bytes("flip-offline")), md5(drive.files.single().bytes))
    }

    @Test fun anotherUploadInTheMiddleOfASyncIsNeverOverwritten() {
        val (flip, konkr) = world("Flip2", "KONKR")
        play(flip, "psp/SAVE.BIN", bytes("v1")); sync(flip, drive); sync(konkr, drive)
        play(flip, "psp/SAVE.BIN", bytes("flip-2"))
        play(konkr, "psp/SAVE.BIN", bytes("konkr-2"))
        // The KONKR uploads after the Flip listed Drive and before the Flip sends.
        drive.beforePush["psp/SAVE.BIN"] = { sync(konkr, drive) }
        sync(flip, drive)
        // The Flip sees Drive moved: nothing sent, a conflict; Drive keeps the KONKR's save, the Flip keeps its own.
        assertEquals(listOf("psp/SAVE.BIN"), flip.conflicts)
        assertEquals(md5(bytes("konkr-2")), md5(drive.files.single().bytes))
        assertEquals(md5(bytes("flip-2")), md5(flip.local.getValue("psp/SAVE.BIN")))
    }

    @Test fun twoNewSavesUploadedAtOnceNeverSilentlyReplaceEachOther() {
        val (flip, konkr) = world("Flip2", "KONKR")
        play(flip, "nds/Pokemon (Europe).sav", bytes("flip-new"))
        play(konkr, "nds/Pokemon (Europe).sav", bytes("konkr-new"))
        // Both list an empty Drive; the KONKR's whole sync happens inside the Flip's upload: two files with one name.
        drive.beforeUpload["nds/Pokemon (Europe).sav"] = { sync(konkr, drive) }
        sync(flip, drive)
        assertEquals(2, drive.files.size)
        repeat(3) { sync(flip, drive); sync(konkr, drive) }
        // Whichever twin counts, the device whose own save is NOT it is asked - it is never replaced silently.
        val chosen = CloudSync.pickTwin(drive.files.map { it.meta() })
        val loser = if (chosen.device == "Flip2") konkr else flip
        assertTrue(loser.conflicts.isNotEmpty())
        assertEquals(latest.getValue(loser.name to "nds/Pokemon (Europe).sav"), md5(loser.local.getValue("nds/Pokemon (Europe).sav")))
        // The release table (no lineage check) would have downloaded over it - the case this update closes.
        val l = md5(loser.local.getValue("nds/Pokemon (Europe).sav")); val b = loser.base.getValue("nds/Pokemon (Europe).sav")
        assertEquals(SyncDecision.Action.DOWNLOAD, SyncDecision.decide(SyncDecision.Input(l, chosen.md5, b.md5)))
        assertEquals(SyncDecision.Action.CONFLICT, SyncDecision.decide(SyncDecision.Input(l, chosen.md5, b.md5, chosen.id, b.remoteId)))
        // After the player decides, both handhelds agree and nothing was lost.
        resolve(loser, drive, keepLocal = true)
        repeat(3) { devices.forEach { d -> sync(d, drive); if (d.conflicts.isNotEmpty()) resolve(d, drive, keepLocal = false) } }
        assertEquals(md5(flip.local.values.single()), md5(konkr.local.values.single()))
    }

    @Test fun aMultiFileSaveIsDownloadedWholeOrNotAtAll() {
        val (flip, konkr) = world("Flip2", "KONKR")
        val unit = "PSP/SAVEDATA/ULUS10391"
        val files = listOf("$unit/DATA.BIN", "$unit/PARAM.SFO", "$unit/ICON0.PNG")
        files.forEach { play(flip, it, bytes("$it-1")) }
        sync(flip, drive, ::psp); sync(konkr, drive, ::psp)
        files.forEach { play(flip, it, bytes("$it-2")) }
        sync(flip, drive, ::psp)
        // 1. The second file can't be fetched: the KONKR keeps all three old files - never a mix.
        drive.failDownloads = setOf(files[1])
        sync(konkr, drive, ::psp)
        files.forEach { assertEquals(md5(bytes("$it-1")), md5(konkr.local.getValue(it))) }
        // 2. It arrives mangled: same.
        drive.failDownloads = emptySet(); drive.corruptDownloads = setOf(files[2])
        sync(konkr, drive, ::psp)
        files.forEach { assertEquals(md5(bytes("$it-1")), md5(konkr.local.getValue(it))) }
        // 3. Storage fails on the second write: the first is put back.
        drive.corruptDownloads = emptySet(); konkr.failWriteAt = 1
        sync(konkr, drive, ::psp)
        files.forEach { assertEquals(md5(bytes("$it-1")), md5(konkr.local.getValue(it))) }
        // 4. Then it works: all three new, all three old ones backed up.
        sync(konkr, drive, ::psp)
        files.forEach { assertEquals(md5(bytes("$it-2")), md5(konkr.local.getValue(it))) }
        val backed = konkr.backupBytes().map { md5(it) }.toSet()
        files.forEach { assertTrue(md5(bytes("$it-1")) in backed) }
    }

    @Test fun aSaveIsNeverReplacedWhenItsBackupCantBeMade() {
        val (flip, konkr) = world("Flip2", "KONKR")
        play(flip, "gba/Metroid.sav", bytes("a")); sync(flip, drive); sync(konkr, drive)
        play(flip, "gba/Metroid.sav", bytes("b")); sync(flip, drive)
        // The backup folder can't be written (a file is in its way).
        konkr.backupsDir.deleteRecursively(); konkr.backupsDir.writeText("not a folder")
        sync(konkr, drive)
        assertEquals(md5(bytes("a")), md5(konkr.local.getValue("gba/Metroid.sav")))
        konkr.backupsDir.delete(); konkr.backupsDir.mkdirs()
        sync(konkr, drive)
        assertEquals(md5(bytes("b")), md5(konkr.local.getValue("gba/Metroid.sav")))
    }

    @Test fun similarNamesStaySeparateAndNamesAreNeverChanged() {
        val (flip, konkr) = world("Flip2", "KONKR")
        val names = listOf("snes/Chrono Trigger (USA).srm", "snes/Chrono Trigger (Japan).srm", "snes/Chrono Trigger (USA) [Hack].srm", "snes/chrono trigger (usa).srm")
        names.forEach { play(flip, it, bytes(it)) }
        sync(flip, drive); sync(konkr, drive)
        assertEquals(names.sorted(), konkr.local.keys.toList())
        names.forEach { assertEquals(md5(bytes(it)), md5(konkr.local.getValue(it))) }
        assertEquals(names.sorted(), drive.files.map { it.name }.sorted())
    }

    @Test fun aSaveDeletedOnOneHandheldIsNeverDeletedElsewhere() {
        val (flip, konkr) = world("Flip2", "KONKR")
        play(flip, "n64/Mario.eep", bytes("m")); sync(flip, drive); sync(konkr, drive)
        flip.local.remove("n64/Mario.eep")
        sync(flip, drive); sync(konkr, drive)
        assertEquals(md5(bytes("m")), md5(konkr.local.getValue("n64/Mario.eep")))
        assertEquals(1, drive.files.size)
    }

    @Test fun threeHandheldsRandomlyPlayingAndSyncingNeverLoseProgress() {
        repeat(25) { seed ->
            latest.clear()
            val rnd = Random(seed)
            world("Flip2", "KONKR", "Thor")
            devices[1].dateStep = 2_000 // one handheld keeps its saves on a FAT / exFAT card
            val names = listOf("gb/A.srm", "gba/B.sav", "PSP/SAVEDATA/ULUS1/DATA.BIN", "PSP/SAVEDATA/ULUS1/PARAM.SFO")
            repeat(120) { step ->
                val d = devices[rnd.nextInt(devices.size)]
                when (rnd.nextInt(10)) {
                    in 0..3 -> play(d, names[rnd.nextInt(names.size)], bytes("s$seed-$step-${d.name}", 64 + rnd.nextInt(3)))
                    in 4..6 -> sync(d, drive, ::psp)
                    7 -> {
                        // Another handheld syncs in the middle of this one's sync: after the listing, or (harsher, the twin
                        // case and the last milliseconds before Drive takes the bytes) inside the upload itself.
                        val other = devices.filter { it !== d }.random(rnd)
                        val name = names[rnd.nextInt(names.size)]
                        if (rnd.nextBoolean()) drive.beforePush[name] = { sync(other, drive, ::psp) }
                        else drive.beforeUpload[name] = { sync(other, drive, ::psp) }
                        sync(d, drive, ::psp)
                        drive.beforeUpload.clear(); drive.beforePush.clear()
                    }
                    8 -> {
                        drive.failDownloads = setOf(names[rnd.nextInt(names.size)]); d.failWriteAt = rnd.nextInt(3) - 1
                        sync(d, drive, ::psp)
                        drive.failDownloads = emptySet(); d.failWriteAt = -1
                    }
                    else -> if (d.conflicts.isNotEmpty()) resolve(d, drive, keepLocal = rnd.nextBoolean(), ::psp)
                }
                check()
            }
            // Settle: everyone syncs and decides until nothing is open; then every handheld holds Drive's version of every save.
            repeat(6) { devices.forEach { d -> sync(d, drive, ::psp); if (d.conflicts.isNotEmpty()) resolve(d, drive, keepLocal = false, ::psp) } }
            val remote = drive.files.groupBy { it.name }.mapValues { (_, s) -> CloudSync.pickTwin(s.map { it.meta() }).md5 }
            devices.forEach { d -> d.local.forEach { (rel, b) -> assertEquals("seed $seed ${d.name} $rel", remote[rel], md5(b)) } }
        }
    }

    @Test fun aSecondSaveWithinOneDateStepIsSeenOnACard() {
        val (flip, konkr) = world("Flip2", "KONKR")
        konkr.dateStep = 2_000 // FAT / exFAT card: dates in 2 s steps
        clock = 1_800_000_000_000L - 1_000 // the next save lands at the start of a 2 s step
        play(konkr, "gba/Zelda.sav", bytes("k1"))
        sync(konkr, drive) // hashed now, the save's date in the same step
        play(konkr, "gba/Zelda.sav", bytes("k2")) // saved again one second later: same size, same (rounded) date
        sync(flip, drive); play(flip, "gba/Zelda.sav", bytes("f3")); sync(flip, drive)
        // The KONKR's second save is seen (read again): a conflict - not "unchanged" and replaced by the Flip's.
        sync(konkr, drive)
        assertEquals(listOf("gba/Zelda.sav"), konkr.conflicts)
        assertEquals(md5(bytes("k2")), md5(konkr.local.getValue("gba/Zelda.sav")))
    }

    // ---------- the table itself ----------

    @Test fun theDecisionTable() {
        fun d(l: String?, r: String?, b: String?, rid: String? = "x", bid: String? = "x", uploadOnly: Boolean = false) =
            SyncDecision.decide(SyncDecision.Input(l, r, b, rid, bid, uploadOnly))
        assertEquals(SyncDecision.Action.NOTHING, d(null, null, null))
        assertEquals(SyncDecision.Action.UPLOAD, d("1", null, null))
        assertEquals(SyncDecision.Action.DOWNLOAD, d(null, "1", null))
        assertEquals(SyncDecision.Action.NOTHING, d(null, "1", null, uploadOnly = true))
        assertEquals(SyncDecision.Action.IN_SYNC, d("1", "1", null))
        assertEquals(SyncDecision.Action.IN_SYNC, d("AB", "ab", "0"))
        assertEquals(SyncDecision.Action.CONFLICT, d("1", "2", null))
        assertEquals(SyncDecision.Action.UPLOAD, d("2", "1", "1"))
        assertEquals(SyncDecision.Action.DOWNLOAD, d("1", "2", "1"))
        assertEquals(SyncDecision.Action.NOTHING, d("1", "2", "1", uploadOnly = true))
        assertEquals(SyncDecision.Action.CONFLICT, d("2", "3", "1"))
        // Finding 9: Drive's file is another file than the base came from.
        assertEquals(SyncDecision.Action.CONFLICT, d("1", "2", "1", rid = "y", bid = "x"))
        // Older records without an id keep the old behaviour.
        assertEquals(SyncDecision.Action.DOWNLOAD, d("1", "2", "1", rid = "y", bid = null))
        assertEquals(SyncDecision.Action.DOWNLOAD, d("1", "2", "1", rid = "y", bid = ""))
        // Multi-file saves changed on both sides.
        assertEquals(SyncDecision.Action.CONFLICT, SyncDecision.decide(SyncDecision.Input("1", "2", "1", inMixedUnit = true)))
        assertEquals(SyncDecision.Action.NOTHING, SyncDecision.decide(SyncDecision.Input("1", null, "1", inMixedCard = true)))
        assertEquals(SyncDecision.Action.CONFLICT, SyncDecision.decide(SyncDecision.Input("1", "2", "1", inMixedCard = true)))
    }

    @Test fun theTransactionCleansUpAndSkipsWhenTheSaveChangedMeanwhile() {
        val temps = mutableListOf<File>()
        val result = SaveUnitTransaction.run(
            listOf(SaveUnitTransaction.Item("a", null, 0), SaveUnitTransaction.Item("b", null, 0)),
            fetch = { item -> if (item.rel == "b") null else File.createTempFile("txn", ".part", tmpRoot).also { temps += it } },
            md5 = { "x" }, backup = { fail("nothing is backed up before every file is fetched"); null },
            restore = { _, _: Unit -> }, write = { _, _ -> fail("nothing is written") }
        )
        assertNull(result)
        assertFalse(temps.any { it.exists() })
    }

    /** The model's multi-file saves: one PSP save folder each (the real [SaveLayouts.unitOf] has its own tests). */
    private fun psp(rel: String): String? = if (rel.startsWith("PSP/SAVEDATA/")) rel.substringBeforeLast('/') else null
}
