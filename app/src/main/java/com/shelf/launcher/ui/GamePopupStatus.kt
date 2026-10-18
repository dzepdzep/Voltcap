package com.shelf.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Speed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.data.CloudSync
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.data.SyncStatusStore
import com.shelf.launcher.device.tune.AutoTdpMode
import com.shelf.launcher.device.tune.FanMode
import com.shelf.launcher.device.tune.PowerTier
import com.shelf.launcher.device.tune.TuneProfile
import com.shelf.launcher.device.tune.TuneStore
import com.shelf.launcher.device.tune.Tuner
import com.shelf.launcher.device.tune.Underclock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Update 213: what the game popup (GameWindow.kt, GameWindowBanner.kt) says about the game you picked — its performance
 * profile (a Performance page / tab, a one-line summary) and its save sync (a small status line on every page).
 *
 * Performance uses the tuner's own store and rules (TuneStore, Tuner.resolve): a game's own profile, else its emulator's
 * or app's ("pkg:"), else its console's ("plat:", Settings › Performance › Profiles per console), else the device-wide
 * one. A change here always goes into this game's own profile (made from what it used until then), never into the
 * console's or the device's; "Use … again" removes it. A whole profile is per game, so every row here really is
 * per game. Tuner.profileChanged applies it at once only to a game the tuner is running now; otherwise it's used when
 * the game starts.
 *
 * Save sync reads the per-game record (SyncStatusStore) and the sync settings; it never starts a sync or touches a
 * conflict by itself. "Up to date" only when this game's own record says so.
 */

/** Where a game's performance settings come from. */
internal class GamePerf(
    val key: String,
    val gameName: String,
    val own: TuneProfile?,
    /** The profile it uses without its own, and what that is ("PS2 default", "Device default"…). */
    val inherited: TuneProfile,
    val inheritedFrom: String
) {
    val effective: TuneProfile get() = own ?: inherited
    val source: String get() = if (own != null) "This game" else inheritedFrom
    /** "Balanced · AutoTDP Smooth · PS2 default". */
    val summary: String get() = listOfNotNull(
        effective.tier.label,
        "AutoTDP ${effective.autoMode.label}".takeIf { effective.autoTdp }
    ).joinToString(" · ") + " · " + source
}

/** Live: the game's performance profile, and saving a change to it (null when this handheld can't be tuned). */
internal class GamePerfState(val perf: GamePerf, val put: ((TuneProfile) -> TuneProfile) -> Unit, val inherit: () -> Unit, val running: Boolean, val applied: TuneProfile?)

@Composable
internal fun rememberGamePerf(g: Game, packageName: String?): GamePerfState? {
    if (!Tuner.supported) return null
    val context = LocalContext.current
    val store = remember { TuneStore(context) }
    val version = remember { mutableIntStateOf(0) }
    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
    val v = version.intValue
    // (Changes saved anywhere — Advanced…, the tuner, a tile — count too: TuneStore.edits.)
    val edits = TuneStore.edits.intValue
    val perf = remember(g.stableKey, packageName, v, sync, edits) {
        val own = store.forKey(g.stableKey)
        val pkg = packageName?.let { store.forKey("pkg:$it") }
        val platform = g.platform.takeIf { g.sourceType == SourceType.ROM && it.isNotBlank() }
        val cons = if (store.consoleProfiles && platform != null) store.forKey("plat:$platform") else null
        val (inh, from) = when {
            pkg != null -> pkg to (if (g.sourceType == SourceType.APP) "App default" else "Emulator default")
            cons != null -> cons to "${com.shelf.launcher.data.ConsoleNames.display(platform!!)} default"
            else -> store.global() to "Device default"
        }
        GamePerf(g.stableKey, g.name, own, inh, from)
    }
    val status by Tuner.status.collectAsState()
    // (The tuner names the game it runs a profile for by its name.)
    val running = status.source == g.name
    return GamePerfState(
        perf,
        put = { change ->
            store.setForKey(g.stableKey, change(perf.own ?: perf.inherited))
            Tuner.profileChanged(context, g.stableKey)
            version.intValue = v + 1
        },
        inherit = {
            store.setForKey(g.stableKey, null)
            Tuner.profileChanged(context, g.stableKey)
            version.intValue = v + 1
        },
        running = running,
        applied = if (running) status.profile else null
    )
}

/** One row of the Performance page: its name, what it is now (and from where), and what A / a tap does. */
internal class PerfRow(val label: String, val value: String, val destructive: Boolean = false, val onClick: (() -> Unit)?)

/** The Performance page's rows (the same in every popup style). [hasFan]: the handheld's fan can be set (null: not known yet). */
internal fun perfRows(s: GamePerfState, hasFan: Boolean?, onAdvanced: () -> Unit): List<PerfRow> {
    val p = s.perf.effective
    val from = if (s.perf.own == null) " · " + s.perf.inheritedFrom else ""
    fun <T> next(list: List<T>, now: T): T = list[(list.indexOf(now) + 1).mod(list.size)]
    val tiers = listOf(PowerTier.OFF, PowerTier.BALANCED, PowerTier.SAVER, PowerTier.ECO, PowerTier.MAX)
    val ucs = listOf(Underclock.AUTO, Underclock.OFF, Underclock.LIGHT, Underclock.MEDIUM, Underclock.HEAVY)
    val rows = mutableListOf<PerfRow>()
    rows += PerfRow("Power mode", p.tier.label + " · " + p.tier.blurb + from) {
        s.put { it.copy(tier = if (it.tier in tiers) next(tiers, it.tier) else PowerTier.OFF) }
    }
    val modes = listOf<AutoTdpMode?>(null) + AutoTdpMode.values().toList()
    rows += PerfRow("AutoTDP", (if (p.autoTdp) p.autoMode.label + " · up to " + p.autoMode.ceilingW.toInt() + " W" else "Off") + from) {
        s.put { cur ->
            when (val n = next(modes, if (cur.autoTdp) cur.autoMode else null)) {
                null -> cur.copy(autoTdp = false)
                else -> cur.copy(autoTdp = true, autoMode = n)
            }
        }
    }
    if (p.autoTdp) {
        val fps = listOf(0, 30, 40, 45, 60)
        rows += PerfRow("AutoTDP target", (if (p.targetFps <= 0) "Worked out from the game" else "${p.targetFps} fps") + from) {
            s.put { it.copy(targetFps = next(fps, if (it.targetFps in fps) it.targetFps else 0)) }
        }
    }
    rows += PerfRow(
        "Underclock",
        p.underclock.label + (if (p.underclock == Underclock.AUTO) " (now ${p.effectiveUnderclock.label})" else "") + from
    ) { s.put { it.copy(underclock = if (it.underclock in ucs) next(ucs, it.underclock) else Underclock.AUTO) } }
    when (hasFan) {
        false -> rows += PerfRow("Fan", "Not adjustable on this handheld", onClick = null)
        else -> {
            // (Voltcap's own curve / hold-temp only when the profile has one already — set them in Advanced.)
            val fans = FanMode.values().filter { it != FanMode.CURVE && it != FanMode.HOLD_TEMP }
            rows += PerfRow("Fan", p.fan.label + from) { s.put { it.copy(fan = if (it.fan in fans) next(fans, it.fan) else FanMode.SYSTEM) } }
        }
    }
    rows += PerfRow("Advanced…", "CPU and GPU limits, governor, screen rate, buttons, what it cost", onClick = onAdvanced)
    if (s.perf.own != null) rows += PerfRow("Use the ${s.perf.inheritedFrom} again", "Removes this game's own settings", destructive = true) { s.inherit() }
    return rows
}

/** "For this game. Applied when this game launches." — or, running, what's applied now. */
internal fun perfNote(s: GamePerfState): String =
    if (s.running && s.applied != null) "For this game. Running now: changes apply at once · now ${s.applied.tier.label}" +
        (if (s.applied.autoTdp) " · AutoTDP ${s.applied.autoMode.label}" else "")
    else "For this game. Applied when this game launches. Heat guard and the screen-off profile are device-wide (Settings › Performance)."

/** Whether the handheld's own fan setting can be read (so set) — a root read, once, off the main thread. */
@Composable
internal fun rememberHasFan(): Boolean? {
    val v by produceState<Boolean?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { com.shelf.launcher.device.tune.TuneHardware.fanMode() != null }.getOrDefault(false) }
    }
    return v
}

// (Update 231, #81: the classic details window has a Performance button — it opens this game's own quick power settings.)
/**
 * Update 231: this game's quick performance settings as a small popup — the same rows as the game popup's Performance
 * page ([perfRows]: power mode, AutoTDP, underclock, fan, Advanced…), saved into this game's own profile. Opened from
 * the classic details window (GameDetailsOverlay, [DetailsAction.PERFORMANCE]); [onAdvanced] opens the game's settings.
 */
@Composable
fun GamePerfQuickDialog(game: Game, perfPackage: String?, onAdvanced: () -> Unit, onDismiss: () -> Unit) {
    val s = rememberGamePerf(game, perfPackage)
    val hasFan = if (s != null) rememberHasFan() else null
    val firstFocus = remember { FocusRequester() }
    ShelfMenuDialog(
        title = gameDisplayName(game.name),
        subtitle = if (s != null) "Performance · now " + s.perf.summary else "Performance",
        onDismiss = onDismiss,
        icon = Icons.Filled.Speed
    ) {
        if (s == null) {
            MutedNote("Performance settings aren't available on this handheld.")
        } else {
            perfRows(s, hasFan, onAdvanced).forEachIndexed { i, row ->
                key(row.label) { PerfQuickRow(row, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) }
            }
            MutedNote(perfNote(s))
            // The first row takes the D-pad (the dialog's window may need a frame before it can).
            LaunchedEffect(Unit) {
                repeat(5) {
                    if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                    kotlinx.coroutines.delay(32)
                }
            }
        }
    }
}

/** One row of [GamePerfQuickDialog]: its name and what it is now; A / a tap changes it (a row without an action can still be reached). */
@Composable
private fun PerfQuickRow(row: PerfRow, modifier: Modifier) {
    val shape = uiRounded(16.dp)
    val click = row.onClick
    Column(
        modifier
            .fillMaxWidth()
            .voltSurface(shape)
            .then(if (click != null) Modifier.shelfClickable(shape) { click() } else Modifier.shelfFocusable(shape))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            row.label, color = if (row.destructive) Color(0xFFFF8A80) else ShelfTheme.colors.textPrimary, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(row.value, color = ShelfTheme.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** How a game's save-sync state reads in the popup. */
internal enum class SyncLook { OK, BUSY, WAIT, ATTENTION, FAIL, OFFLINE, UNKNOWN, OFF }

/** The game's save-sync line: words, kind, icon, and whether a tap opens Settings › Sync (not set up) or the game's sync. */
internal class GameSyncView(
    val text: String, val kind: SyncLook, val setUp: Boolean = false,
    /** Update 225: nothing to open (an Android app: its saves can't be synced) — a tap does nothing. */
    val inert: Boolean = false
) {
    val icon: ImageVector get() = when (kind) {
        SyncLook.OK -> Icons.Filled.CloudDone
        SyncLook.BUSY -> Icons.Filled.Sync
        SyncLook.WAIT -> Icons.Filled.Schedule
        SyncLook.ATTENTION -> Icons.Filled.Warning
        SyncLook.FAIL -> Icons.Filled.ErrorOutline
        SyncLook.OFFLINE, SyncLook.OFF -> Icons.Filled.CloudOff
        SyncLook.UNKNOWN -> Icons.Filled.HelpOutline
    }
    /** Needs you: shown stronger (and with its own icon, never by colour alone). */
    val urgent: Boolean get() = kind == SyncLook.ATTENTION || kind == SyncLook.FAIL
}

/**
 * The picked game's save sync, live (it follows the sync as it runs). Reads only what's already known: no sync, no
 * network. [syncOn]: the game's own "Sync saves" (Game settings), null = as Settings › Sync.
 */
@Composable
internal fun rememberGameSyncView(g: Game, syncOn: Boolean?): GameSyncView {
    val context = LocalContext.current
    val sync by CloudSync.state.collectAsState()
    val all by SyncStatusStore.all.collectAsState()
    val s = sync.settings
    // Whether a synced folder holds this game's saves (no file reading of saves, no network).
    val matched by produceState<Boolean?>(null, g.stableKey, s.connected, s.syncSaves, s.folders) {
        value = withContext(Dispatchers.IO) { runCatching { CloudSync.canWatchSaves(context, g) }.getOrDefault(false) }
    }
    val st0 = all[g.stableKey]
    // Update 222 (#106): what the files say, not only the last job — a save written after the last upload (size / date
    // differ from what was sent: no reading, no network) and when they last went to / came from Drive for certain.
    val probe by produceState<Pair<Int, Pair<Long, String?>?>?>(null, g.stableKey, matched, st0?.updatedAt, sync.running) {
        value = if (matched != true) null else withContext(Dispatchers.IO) {
            runCatching {
                CloudSync.changedSaves(context, CloudSync.ScopedGame.of(g)).size to CloudSync.lastConfirmedSync(context, g)
            }.getOrNull()
        }
    }
    val unsent = (probe?.first ?: 0) > 0
    val sending = if (com.shelf.launcher.data.SaveObserver.watchingKey() == g.stableKey) " · sending soon" else ""
    if (!s.connected) return GameSyncView("Save sync isn't set up", SyncLook.OFF, setUp = true)
    if (!s.syncSaves) return GameSyncView("Save sync is off", SyncLook.OFF, setUp = true)
    if (g.sourceType != SourceType.ROM) return GameSyncView("No save sync for apps", SyncLook.OFF, inert = true)
    if (syncOn == false) return GameSyncView("Save sync off for this game", SyncLook.OFF)
    if (sync.needsSignIn) return GameSyncView("Sign in to Google Drive again", SyncLook.ATTENTION, setUp = true)
    val st = all[g.stableKey]
    val everything = if (sync.running && st?.state != SyncState.UPLOADING && st?.state != SyncState.DOWNLOADING) " · syncing all games…" else ""
    return when (st?.state) {
        SyncState.SYNCED -> if (unsent) GameSyncView("Newer save on this device · not sent yet$sending", SyncLook.WAIT)
            else GameSyncView("Saves up to date" + (if (st.lastSuccessAt > 0L) " · ${syncAgo(st.lastSuccessAt)}" else "") + everything, SyncLook.OK)
        SyncState.UPLOADING -> GameSyncView("Uploading saves…", SyncLook.BUSY)
        SyncState.DOWNLOADING -> GameSyncView("Downloading saves…", SyncLook.BUSY)
        SyncState.PENDING -> GameSyncView((if (st.lastSuccessAt > 0L) "Changes waiting to sync" else "Waiting for the first sync") + everything, SyncLook.WAIT)
        SyncState.CONFLICT -> GameSyncView("Save conflict · choose which to keep", SyncLook.ATTENTION)
        SyncState.ERROR -> GameSyncView("Sync failed · Retry", SyncLook.FAIL)
        SyncState.OFFLINE -> GameSyncView("Offline · will retry", SyncLook.OFFLINE)
        SyncState.NEEDS_SIGN_IN -> GameSyncView("Sign in to Google Drive again", SyncLook.ATTENTION, setUp = true)
        null -> when (matched) {
            false -> GameSyncView("No save folder for this game", SyncLook.OFF, setUp = true)
            true -> {
                val last = probe?.second
                when {
                    unsent && last == null -> GameSyncView("Not on Google Drive yet$sending", SyncLook.WAIT)
                    unsent -> GameSyncView("Newer save on this device · not sent yet$sending", SyncLook.WAIT)
                    last != null -> GameSyncView("Saves up to date · ${syncAgo(last.first)}" + (last.second?.let { " · from $it" } ?: "") + everything, SyncLook.OK)
                    else -> GameSyncView("Not checked yet" + everything, SyncLook.UNKNOWN)
                }
            }
            null -> GameSyncView("Save sync", SyncLook.UNKNOWN)
        }
    }
}
