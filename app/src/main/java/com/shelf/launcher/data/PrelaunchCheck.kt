package com.shelf.launcher.data

/**
 * Update 529 (save sync P0, finding 5): before a game starts, is this device's save known to match Google Drive?
 *
 * Before, Play never waited: the check started when the game's window opened ([com.shelf.launcher.LauncherViewModel.prepareLaunch])
 * and Play STOPPED it if it was still running - the game then started on whatever this device had, even when the other handheld
 * had uploaded newer progress. Now Play waits for that check (or starts one), at most [TIMEOUT_MS]; a check that doesn't finish,
 * fails or finds a conflict never lets the game start silently on an unverified save - the player decides (Play anyway / Retry,
 * or the conflict dialog). A game whose save was confirmed against Drive moments ago ([FRESH_MS]) starts straight away.
 */
internal object PrelaunchCheck {
    /** A both-ways check younger than this counts as current (Drive's change feed is also polled every 90 s on screen). */
    const val FRESH_MS = 60_000L
    /** The longest Play waits for Drive before asking. */
    const val TIMEOUT_MS = 10_000L

    /** True when Play must check Drive first. [active]: sync is on for this game and it isn't a QA / safe-mode session. */
    fun needed(active: Boolean, status: GameSyncStatus?, now: Long): Boolean {
        if (!active) return false
        val st = status ?: return true
        // Only a both-ways check that ended cleanly counts; an upload, a failure, a conflict or a pending change never does.
        if (st.state != SyncState.SYNCED) return true
        return st.checkedAt <= 0L || now - st.checkedAt > FRESH_MS || st.checkedAt > now + 5_000L
    }

    enum class Next { PLAY, ASK, CONFLICT }

    /** What happens after the check: [outcome] null = it didn't finish in time. */
    fun next(outcome: SyncOutcome?): Next = when {
        outcome == null -> Next.ASK
        outcome.conflicts > 0 -> Next.CONFLICT
        // (An emulator still held the save: Drive's side was not applied here, so it is not known to be current.)
        outcome.heldBack -> Next.ASK
        // (Update 529c: another handheld was still sending a multi-file save of this game - Drive's newest wasn't applied yet.)
        outcome.unitsHeld > 0 -> Next.ASK
        outcome.ok -> Next.PLAY
        else -> Next.ASK
    }
}
