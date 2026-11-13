package com.shelf.launcher.data

/**
 * Update 529c (save sync P0, the risks SAVE_SYNC_529.md section 4 left open). Pure rules, no Android types, unit-tested on the JVM
 * (SyncGuards529Test) and in the two-device simulation (SaveSyncSim529Test). CloudSync / DriveClient / SaveWatchWorker apply them.
 */

/**
 * Risk 2 - the last-moment upload race. Drive v3 has no conditional update: if another handheld uploads between our "is Drive's
 * file still the version we decided on?" check and Drive accepting our bytes, our upload becomes the head revision and theirs
 * sits underneath it, unannounced. Drive keeps every revision of a binary file (head revisions: 30 days or 100 revisions), so after
 * each replace we read the file's revision list: every revision between the one we meant to replace and ours is another device's
 * save we replaced without seeing. Those are fetched (checked against their md5), parked in Voltcap/Sync conflicts, pinned on
 * Drive (keepForever) and reported - never silent.
 */
internal object UploadRace {
    data class Revision(val id: String, val md5: String?)

    /**
     * [revisions] oldest first, as Drive lists them. [expectMd5]: the version our decision was made on (what we meant to replace);
     * [sentMd5]: what we uploaded. Returns the revisions that were replaced unseen, or null when it can't be told (ours or the
     * expected one isn't in the list - pruned, or Drive merged them): the caller then reports nothing and the next full sync's
     * three-way comparison still guards the save.
     */
    fun replacedUnseen(revisions: List<Revision>, expectMd5: String, sentMd5: String): List<Revision>? {
        val ours = revisions.indexOfLast { it.md5.equals(sentMd5, ignoreCase = true) }
        if (ours < 0) return null
        val expected = (ours - 1 downTo 0).firstOrNull { revisions[it].md5.equals(expectMd5, ignoreCase = true) } ?: return null
        return revisions.subList(expected + 1, ours).filter {
            // (A revision with the same bytes as ours or as the expected one loses nothing.)
            !it.md5.equals(sentMd5, ignoreCase = true) && !it.md5.equals(expectMd5, ignoreCase = true)
        }
    }
}

/**
 * Risk 3 - a multi-file save (a PS2 folder card, a 3DS / Wii title folder, a PSP save folder) half-updated on Drive. Uploads go file
 * by file, so while one handheld sends a save's files (or after it failed halfway) Drive holds some new and some old files. Locally a
 * download is already all-or-none ([SaveUnitTransaction]), but it could take that mix as "the" version.
 *
 * Now the uploader writes a small manifest LAST, once every file of the save is on Drive and verified: every file's md5. A downloader
 * applies a save only when Drive's files match its manifest exactly; a mismatch means an upload is in progress or broke off - the save
 * is held ("still being updated on Drive"), never applied as a mix, and looked at again by the next sync. Files sent by an older Voltcap
 * (no `unit` mark) don't know about manifests: a mismatch caused only by them is no reason to wait (the old behaviour applies). A save
 * held on this device for longer than [HELD_LIMIT_MS] (this device's own clock, start to now - never compared with another device's)
 * becomes a conflict for the player instead of waiting forever for a handheld that may never come back.
 */
internal object UnitManifest {
    /** Drive file name prefix; never a save name (filtered from every save listing). */
    const val PREFIX = ".voltcap-unit~"
    const val HELD_LIMIT_MS = 24L * 60 * 60 * 1000

    fun nameFor(unit: String): String = PREFIX + unit.replace('/', '~')
    fun isManifest(name: String): Boolean = name.startsWith(PREFIX)

    /** `voltcap-unit 1`, `unit <key>`, then `<md5>\t<rel>` per file, sorted (stable bytes for the same content). */
    fun encode(unit: String, files: Map<String, String>): String = buildString {
        append("voltcap-unit 1\n").append("unit ").append(unit).append('\n')
        files.toSortedMap().forEach { (rel, md5) -> append(md5.lowercase()).append('\t').append(rel).append('\n') }
    }

    /** The manifest's files (rel -> md5), null when it isn't one. */
    fun decode(text: String): Map<String, String>? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "voltcap-unit 1") return null
        return lines.drop(2).filter { it.contains('\t') }.associate { l -> l.substringAfter('\t') to l.substringBefore('\t').lowercase() }
    }

    enum class Verdict { NO_MANIFEST, COMPLETE, INCOMPLETE }

    /** One file of the save as Drive has it now: its md5 and whether a manifest-aware Voltcap sent it. */
    data class OnDrive(val md5: String?, val marked: Boolean)

    /**
     * [manifest]: the save's manifest (null = none on Drive); [drive]: every file of the save on Drive now (rel -> file).
     * COMPLETE: Drive's files are exactly the manifest's. INCOMPLETE: an upload by a manifest-aware Voltcap is in progress or broke
     * off. NO_MANIFEST: nothing to check against (none yet, or only older Voltcaps changed the save since) - as before.
     */
    fun check(manifest: Map<String, String>?, drive: Map<String, OnDrive>): Verdict {
        if (manifest == null) return Verdict.NO_MANIFEST
        val differing = (manifest.keys + drive.keys).filter { rel ->
            val d = drive[rel]
            d?.md5 == null || !d.md5.equals(manifest[rel], ignoreCase = true)
        }
        if (differing.isEmpty()) return Verdict.COMPLETE
        // Only files from older Voltcaps differ (or files the manifest doesn't know, sent by them): the manifest is just stale.
        val byAware = differing.any { rel -> drive[rel]?.marked == true || (drive[rel] == null && manifest.containsKey(rel)) }
        return if (byAware) Verdict.INCOMPLETE else Verdict.NO_MANIFEST
    }

    /** Held since [heldSince] (this device's clock): wait, or hand it to the player. */
    fun heldTooLong(heldSince: Long, now: Long): Boolean = heldSince in 1..now && now - heldSince > HELD_LIMIT_MS
}

/**
 * Risk 4 - "Newest" in the conflict choice compared file dates written by two different clocks. A handheld whose clock is a few
 * minutes wrong (no network time, a dead RTC battery, a manual date) made "newest" pick the older save.
 *
 * Now each upload records the uploading device's clock error against Google's own clock (the HTTP `Date` of Drive's replies,
 * `skew` = device clock - Google's clock), and "Newest" compares both dates corrected to Google's clock. It decides only when the
 * corrected dates are further apart than [MARGIN_MS] (the measurement's own uncertainty); otherwise - or when a skew is unknown
 * (older uploads) and the raw dates are closer than [UNKNOWN_MARGIN_MS], or a date is in the future - it does NOT guess: that
 * save stays open for "Use Drive's" / "Keep this device's", which don't depend on any clock.
 */
internal object ConflictClock {
    const val MARGIN_MS = 2 * 60_000L
    const val UNKNOWN_MARGIN_MS = 15 * 60_000L
    enum class Pick { DRIVE, LOCAL, UNSURE }

    fun newest(localModified: Long, localSkew: Long?, remoteModified: Long, remoteSkew: Long?, now: Long, nowSkew: Long?): Pick {
        if (localModified <= 0L || remoteModified <= 0L) return Pick.UNSURE
        // A date after "now" (corrected) can't be trusted at all.
        val googleNow = now - (nowSkew ?: 0L)
        val l = localModified - (localSkew ?: 0L)
        val r = remoteModified - (remoteSkew ?: 0L)
        if (l > googleNow + MARGIN_MS || r > googleNow + MARGIN_MS) return Pick.UNSURE
        val margin = if (localSkew == null || remoteSkew == null) UNKNOWN_MARGIN_MS else MARGIN_MS
        return when {
            r - l > margin -> Pick.DRIVE
            l - r > margin -> Pick.LOCAL
            else -> Pick.UNSURE
        }
    }
}

/**
 * Pending uploads across a restart: a play session still marked open after the handheld restarted can't still be running (the
 * emulator went down with the device). Told apart with this device's own clocks only: the session started before this boot.
 */
internal object BootSession {
    /** Wall-clock time of this boot = now - time since boot; [slackMs] absorbs a small wall-clock correction (network time). */
    fun startedBeforeBoot(sessionStart: Long, now: Long, elapsedSinceBoot: Long, slackMs: Long = 120_000L): Boolean =
        sessionStart > 0L && sessionStart < now - elapsedSinceBoot - slackMs
}
