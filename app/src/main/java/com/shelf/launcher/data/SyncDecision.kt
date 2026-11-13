package com.shelf.launcher.data

/**
 * Update 529 (save sync P0): what one save file's sync does, from three checksums - this device's file ([Input.local]), Drive's
 * ([Input.remote]) and the version both last agreed on ([Input.base]). It was written inline in [CloudSync.syncFolder]; it is
 * pulled out unchanged (one rule added, below) so the whole table - and two-device runs of it - are unit-tested on the JVM.
 *
 * The table (a three-way merge, like git's):
 *  - only here → upload; only on Drive → download (never deleted anywhere: a save removed here comes back);
 *  - equal → in sync; never synced before and different → conflict;
 *  - Drive still has the base → only this device changed → upload;
 *  - this device still has the base → only Drive changed → download;
 *  - both changed → conflict: nothing is touched, the player decides.
 *  - Multi-file saves (PS2 folder cards, 3DS / Wii title folders, PSP save folders) changed on BOTH sides: every differing file
 *    is a conflict, never a half-and-half mix.
 *
 * Added (finding 9, found in this audit): "this device still has the base" was trusted even when Drive's file is a DIFFERENT
 * file from the one the base was taken from - two handhelds uploading the same new save at once leave two files with one name
 * ("twins"), and the one picked may be the other device's. Its content is then not a newer version of ours but a separate
 * line of progress, and downloading it silently replaced this device's save. Now that case is a conflict.
 */
internal object SyncDecision {
    /** [HELD] (update 529, mid-game sync): Drive has a newer version, but this run may not touch local files (a game may be
     *  running) - it is reported, and applied by the next sync that may (after the game). */
    enum class Action { NOTHING, IN_SYNC, UPLOAD, DOWNLOAD, CONFLICT, HELD }

    data class Input(
        val local: String?,
        val remote: String?,
        val base: String?,
        /** Drive's file id now / the id the base was taken from (null or blank: older records that never stored one). */
        val remoteId: String? = null,
        val baseRemoteId: String? = null,
        val uploadOnly: Boolean = false,
        /** Part of a PS2 folder card changed on both sides. */
        val inMixedCard: Boolean = false,
        /** Part of another multi-file save changed on both sides. */
        val inMixedUnit: Boolean = false
    )

    fun decide(i: Input): Action {
        val l = i.local
        val r = i.remote
        val b = i.base
        if (i.inMixedCard) return if (l != r && l != null && r != null) Action.CONFLICT else Action.NOTHING
        if (i.inMixedUnit && l != null && r != null && !same(l, r)) return Action.CONFLICT
        return when {
            l == null && r == null -> Action.NOTHING
            r == null -> Action.UPLOAD
            l == null -> if (i.uploadOnly) Action.HELD else Action.DOWNLOAD
            same(l, r) -> Action.IN_SYNC
            b == null -> Action.CONFLICT
            same(r, b) -> Action.UPLOAD
            same(l, b) -> when {
                otherLineage(i) -> Action.CONFLICT
                i.uploadOnly -> Action.HELD
                else -> Action.DOWNLOAD
            }
            else -> Action.CONFLICT
        }
    }

    /** Drive's file is not the one this device's base came from (a twin, or the file was removed and made again elsewhere). */
    private fun otherLineage(i: Input): Boolean =
        !i.baseRemoteId.isNullOrBlank() && !i.remoteId.isNullOrBlank() && i.baseRemoteId != i.remoteId

    private fun same(a: String, b: String) = a.equals(b, ignoreCase = true)
}
