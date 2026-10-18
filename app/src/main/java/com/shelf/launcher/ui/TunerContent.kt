package com.shelf.launcher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.device.tune.AutoTdpController
import com.shelf.launcher.device.tune.AutoTdpMode
import com.shelf.launcher.device.tune.FanMode
import com.shelf.launcher.device.tune.GovernorChoice
import com.shelf.launcher.device.tune.PowerTier
import com.shelf.launcher.device.tune.TuneHardware
import com.shelf.launcher.device.tune.TuneProfile
import com.shelf.launcher.device.tune.TuneStore
import com.shelf.launcher.device.tune.Tuner
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the hardware offers, read once (root reads are slow-ish). */
private data class TuneHw(val clusters: List<TuneHardware.Cluster>, val gpu: TuneHardware.Gpu?, val fan: Boolean)

@Composable
private fun rememberTuneHw(): TuneHw? {
    val hw by produceState<TuneHw?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { TuneHw(TuneHardware.clusters(), TuneHardware.gpu(), TuneHardware.hasCustomFan()) }.getOrNull()
        }
    }
    return hw
}

private fun mhz(khz: Int) = if (khz >= 1_000_000) "%.2f GHz".format(khz / 1_000_000f) else "${khz / 1000} MHz"

/**
 * Settings › Performance: Voltcap's own performance tuner. Power tiers, one master slider, per-cluster
 * and GPU limits, governors, AutoTDP, fan control and the screen-off profile, for the whole device.
 * Games and apps can have their own profile in their game settings.
 */
@Composable
fun TunerSettingsContent() {
    val context = LocalContext.current
    val store = remember { TuneStore(context) }
    var profile by remember { mutableStateOf(store.global()) }
    var sleep by remember { mutableStateOf(store.sleepProfile) }
    var onBoot by remember { mutableStateOf(store.applyOnBoot) }
    var led by remember { mutableStateOf(store.ledStatus) }
    var perConsole by remember { mutableStateOf(store.consoleProfiles) }
    var heatGuard by remember { mutableStateOf(store.heatGuard) }
    var heatC by remember { mutableStateOf(store.heatGuardC) }
    var measure by remember { mutableStateOf(store.measure) }
    val status by Tuner.status.collectAsState()
    val hw = rememberTuneHw()

    androidx.compose.runtime.LaunchedEffect(Unit) { Tuner.start(context) }
    // Changed from a Quick Settings tile, the in-game notification or Android's own fan tile while this
    // page is open: show it.
    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
    androidx.compose.runtime.LaunchedEffect(sync) {
        if (sync > 0) {
            profile = store.global(); led = store.ledStatus; sleep = store.sleepProfile
            perConsole = store.consoleProfiles; heatGuard = store.heatGuard; heatC = store.heatGuardC; measure = store.measure
        }
    }

    SettingsHeading("Performance tuner")
    if (hw != null && hw.clusters.isEmpty()) {
        ShelfCard {
            MutedNote(
                "This device doesn't let Voltcap change clock speeds: it needs the handheld's own root service " +
                    "(AYN and Retroid firmware have one). Nothing here will take effect."
            )
        }
        return
    }
    TunerLiveCard(status)
    Spacer(Modifier.height(10.dp))
    TunerEditor(profile, hw) { p ->
        profile = p
        store.setGlobal(p)
        Tuner.profileChanged(context, null)
    }
    Spacer(Modifier.height(10.dp))
    SettingsHeading("Automation")
    SwitchRow(
        Icons.Filled.Bedtime, "Screen-off profile",
        "With the screen off, drop to power-saving clocks; your profile comes back when it turns on.",
        sleep, onChange = { sleep = it; store.sleepProfile = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.SportsEsports, "Profiles per console",
        "A console can have its own profile (set it in any of its games' settings › All … games). Its games use it unless they have their own.",
        perConsole, onChange = { perConsole = it; store.consoleProfiles = it; Tuner.profileChanged(context, null) }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Thermostat, "Heat guard",
        "While a game runs hot, the underclock steps down one level at a time (up to Heavy) and back up once it has cooled down.",
        heatGuard, onChange = { heatGuard = it; store.heatGuard = it; Tuner.profileChanged(context, null) }
    )
    if (heatGuard) {
        ChipFlow {
            listOf(75, 80, 85, 90).forEach { c ->
                Chip("$c °C", accent = heatC == c) { heatC = c; store.heatGuardC = c; Tuner.profileChanged(context, null) }
            }
        }
        MutedNote("Steps down after 8 seconds at or above $heatC °C, back up after 45 seconds below ${heatC - 8} °C.")
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.QueryStats, "Measure each game",
        "Records what each profile costs per game (battery per hour, watts, temperature, frame rate), shown in the game's settings.",
        measure, onChange = { measure = it; store.measure = it; Tuner.profileChanged(context, null) }
    )
    Spacer(Modifier.height(8.dp))
    ShelfCard {
        SectionLabel("Stick lights as a status light")
        MutedNote("Battery: green when full, red when low. Temperature: blue when cool, red when hot. Works during games.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            com.shelf.launcher.device.tune.LedStatus.values().forEach { m ->
                Chip(m.label, accent = led == m) { led = m; Tuner.setLedStatus(context.applicationContext, m) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.RestartAlt, "Apply after a restart",
        "Put the device-wide profile back when the handheld starts.",
        onBoot, onChange = { onBoot = it; store.applyOnBoot = it }
    )
    Spacer(Modifier.height(10.dp))
    ShelfCard {
        SectionLabel("Restore stock")
        MutedNote("Puts every clock, governor and the fan back to how the device had them and turns the tuner off.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Restore stock", accent = false) {
                profile = TuneProfile()
                store.setGlobal(profile)
                Thread { runCatching { Tuner.restoreStock(context.applicationContext) } }.start()
            }
        }
    }
}

/** What's applied right now, with live numbers while the tuner's loop runs. */
@Composable
private fun TunerLiveCard(s: com.shelf.launcher.device.tune.TunerStatus) {
    ShelfCard {
        SectionLabel("Now")
        val p = s.profile
        val parts = buildList {
            add(if (s.sleeping) "Screen-off profile" else "${p.tier.label} · from ${s.source}")
            if (p.autoTdp) add("AutoTDP ${p.autoMode.label}, level ${(s.level * 100).toInt()} %")
            // (Update 237, #85: with AutoTDP moving the underclock, the preset it is on right now.)
            val auc = s.autoUnderclock
            if (auc != null && !s.sleeping) add("Underclock ${auc.label} (set by AutoTDP)")
            else if (p.underclocked) add("Underclock ${p.effectiveUnderclock.label}")
            if (s.heatStep > 0) add("heat guard: ${s.heatStep} step${if (s.heatStep == 1) "" else "s"} lower")
            s.targetFps?.let { add("target $it fps") }
        }
        Text(parts.joinToString(" · "), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val live = listOfNotNull(
            s.fps?.let { "${it.toInt()} fps" },
            s.powerW?.let { "%.1f W".format(it) },
            s.tempC?.let { "${it.toInt()} °C" },
            s.fanPercent?.let { "fan $it %" }
        )
        // Update 144: the note starts with a capital even when the fan reading is the only one.
        if (live.isNotEmpty()) MutedNote(live.joinToString(" · ").replaceFirstChar { it.uppercase() })
        s.note?.let { MutedNote(it) }
        // Update 201: before this update the chip temperature was never found on AYN handhelds, so Curve / Hold temp switched
        // the fan to manual and then never set a speed (it ran at AYN's fixed 50 %). Say so if a reading is still missing.
        if ((p.fan == FanMode.CURVE || p.fan == FanMode.HOLD_TEMP) && s.tempC == null && !s.sleeping) MutedNote(
            "No chip temperature reading yet: until there is one the fan runs at the handheld's own manual speed."
        )
        // Update 201: AYN's root service puts its own clocks back every 10 s; say how often Voltcap had to set the limits again.
        if (s.resets > 0) MutedNote(
            "The handheld put its own clocks back ${s.resets}× since these limits were set (its performance mode does that " +
                "every 10 seconds); Voltcap set them again each time, within about a second."
        )
    }
}

/**
 * Edits one [TuneProfile]. Shared by the device-wide page and each game's own profile.
 * [hw] null while the hardware is still being read: cluster and GPU rows wait for it.
 */
@Composable
internal fun TunerEditor(profile: TuneProfile, hw: Any?, onChange: (TuneProfile) -> Unit) {
    val h = hw as? TuneHw ?: rememberTuneHw()
    val p = profile

    ShelfCard {
        SectionLabel("Power tier")
        MutedNote(PowerTier.values().first { it == p.tier }.blurb)
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            PowerTier.values().forEach { t ->
                Chip(t.label, accent = p.tier == t) {
                    onChange(p.copy(tier = t, governor = if (t == PowerTier.CUSTOM) p.governor else t.governor))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TierBreakdown(p, h)
        if (p.tier != PowerTier.OFF) {
            Spacer(Modifier.height(10.dp))
            PercentSlider("Power target", p.powerTarget, { v -> onChange(p.copy(powerTarget = v.coerceIn(30, 100))) })
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("CPU and GPU", accent = !p.cpuOnly) { onChange(p.copy(cpuOnly = false)) }
                Chip("CPU only", accent = p.cpuOnly) { onChange(p.copy(cpuOnly = true)) }
            }
            MutedNote("Scales every limit below together. CPU only leaves the graphics chip at its tier speed.")
        }
    }

    Spacer(Modifier.height(10.dp))
    UnderclockCard(p, h, onChange)

    if (p.tier == PowerTier.CUSTOM) {
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel("Per-cluster limits")
            if (h == null) MutedNote("Reading the processor…")
            h?.clusters?.forEach { c ->
                val cap = p.clusterCaps[c.name] ?: 100
                val khz = TuneHardware.snap(c, c.maxKhz * cap / 100)
                PercentSlider("${c.name} (${c.cpus}) · ${mhz(khz)}", cap, { v ->
                    onChange(p.copy(clusterCaps = p.clusterCaps + (c.name to v.coerceIn(20, 100))))
                })
                Spacer(Modifier.height(6.dp))
            }
            h?.gpu?.let { g ->
                val lvl = TuneHardware.gpuLevelFor(g, p.gpuCap)
                PercentSlider("GPU · ${g.levelMhz.getOrNull(lvl) ?: 0} MHz", p.gpuCap, { v ->
                    onChange(p.copy(gpuCap = v.coerceIn(20, 100)))
                })
            }
        }
    }

    if (p.tier != PowerTier.OFF) {
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel("Boost and floors")
            if ((h?.clusters?.size ?: 0) >= 3) {
                PercentSlider("Prime core boost", p.primeBoost, { v -> onChange(p.copy(primeBoost = v.coerceIn(30, 100))) })
                MutedNote("The fastest core's top speed. Lowering it trims heat spikes with little cost to most emulators.")
                Spacer(Modifier.height(6.dp))
            }
            PercentSlider("CPU floor", p.cpuFloor, { v -> onChange(p.copy(cpuFloor = v.coerceAtMost(90))) }, valueText = { if (it == 0) "Off" else "$it%" })
            Spacer(Modifier.height(6.dp))
            if (h?.gpu != null || h == null) {
                PercentSlider("GPU floor", p.gpuFloor, { v -> onChange(p.copy(gpuFloor = v.coerceAtMost(90))) }, valueText = { if (it == 0) "Off" else "$it%" })
            }
            MutedNote("Floors stop clocks dropping too low between frames (fewer stutters, more power).")
            Spacer(Modifier.height(8.dp))
            SectionLabel("Governor")
            ChipFlow {
                GovernorChoice.values().forEach { g -> Chip(g.label, accent = p.governor == g) { onChange(p.copy(governor = g)) } }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    ShelfCard {
        SectionLabel("AutoTDP")
        MutedNote(
            "Finds the lowest clocks that still hold the frame rate, under a power limit. Learns each game " +
                "and starts there next time. Needs the performance overlay's FPS reading to work at its best."
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Off", accent = !p.autoTdp) { onChange(p.copy(autoTdp = false)) }
            AutoTdpMode.values().forEach { m ->
                Chip("${m.label} · ${m.ceilingW.toInt()} W", accent = p.autoTdp && p.autoMode == m) {
                    onChange(p.copy(autoTdp = true, autoMode = m, tier = if (p.tier == PowerTier.OFF) PowerTier.MAX else p.tier))
                }
            }
        }
        if (p.autoTdp) {
            Spacer(Modifier.height(8.dp))
            Text("Target frame rate", color = ShelfTheme.textMuted, fontSize = 12.sp)
            ChipFlow {
                Chip("Auto", accent = p.targetFps == 0) { onChange(p.copy(targetFps = 0)) }
                AutoTdpController.TARGETS.forEach { f -> Chip("$f", accent = p.targetFps == f) { onChange(p.copy(targetFps = f)) } }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    ShelfCard {
        SectionLabel("Fan")
        val custom = h?.fan == true
        ChipFlow {
            FanMode.values().filter { custom || (it != FanMode.CURVE && it != FanMode.HOLD_TEMP) }.forEach { f ->
                Chip(f.label, accent = p.fan == f) { onChange(p.copy(fan = f)) }
            }
        }
        when (p.fan) {
            FanMode.CURVE -> MutedNote("Fan speed follows the chip temperature: " +
                p.fanCurve.joinToString(" · ") { "${it.tempC} °C → ${it.percent} %" })
            FanMode.HOLD_TEMP -> {
                Spacer(Modifier.height(6.dp))
                Text("Hold the chip at", color = ShelfTheme.textMuted, fontSize = 12.sp)
                ChipFlow {
                    listOf(60, 65, 70, 75, 80).forEach { t -> Chip("$t °C", accent = p.holdTempC == t) { onChange(p.copy(holdTempC = t)) } }
                }
                MutedNote("Uses as little fan as it can while keeping that temperature.")
            }
            FanMode.SYSTEM -> MutedNote("The device's own fan setting.")
            else -> MutedNote("The handheld's own ${p.fan.label.lowercase()} fan mode.")
        }
        if (!custom && h != null) MutedNote("Custom curves need a fan Voltcap can drive directly (AYN Odin / Thor).")
    }
}

/**
 * Underclock, ClusterTune / PULSE style: a hard top speed per CPU cluster and for the GPU, on top of the
 * power tier (AutoTDP and the governor work underneath it). Presets, or exact speeds per cluster.
 * Also on the R1 menu, the in-game panel and an Android Quick Settings tile.
 */
@Composable
private fun UnderclockCard(p: TuneProfile, h: TuneHw?, onChange: (TuneProfile) -> Unit) {
    ShelfCard {
        SectionLabel("Underclock")
        MutedNote(
            p.underclock.blurb + ". A ceiling on top of the power tier: nothing goes faster than this." +
                // (Update 237, #85: with AutoTDP on, "With power" is AutoTDP's to move within its style's range.)
                (if (p.underclock == com.shelf.launcher.device.tune.Underclock.AUTO && p.autoTdp)
                    " AutoTDP ${p.autoMode.label} moves it between Off and ${p.autoMode.ucDeepest.label} with the game, starting at ${p.effectiveUnderclock.label}."
                else if (p.underclock == com.shelf.launcher.device.tune.Underclock.AUTO) " Right now: ${p.effectiveUnderclock.label}."
                else if (p.autoTdp && p.underclock != com.shelf.launcher.device.tune.Underclock.OFF) " AutoTDP stays under it."
                else "")
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            com.shelf.launcher.device.tune.Underclock.values().forEach { u ->
                Chip(u.label, accent = p.underclock == u) {
                    // Custom starts from what the chosen preset was, so it's a small step from there.
                    val fresh = u == com.shelf.launcher.device.tune.Underclock.CUSTOM && p.underclockKhz.isEmpty() && p.underclockGpuMhz == 0 && h != null
                    if (fresh && h != null) {
                        val seed = h.clusters.mapIndexedNotNull { i, c -> Tuner.underclockKhz(p, c, i, h.clusters.size)?.let { c.name to it } }.toMap()
                        val gpuSeed = h.gpu?.let { g -> Tuner.underclockGpuLevel(p, g)?.let { g.levelMhz.getOrNull(it) } } ?: 0
                        onChange(p.copy(underclock = u, underclockKhz = seed, underclockGpuMhz = gpuSeed))
                    } else onChange(p.copy(underclock = u))
                }
            }
        }
        val clusters = h?.clusters.orEmpty()
        if (h == null) MutedNote("Reading the processor…")
        if (p.effectiveUnderclock != com.shelf.launcher.device.tune.Underclock.OFF && clusters.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val custom = p.underclock == com.shelf.launcher.device.tune.Underclock.CUSTOM
            clusters.forEachIndexed { i, c ->
                val top = Tuner.underclockKhz(p, c, i, clusters.size) ?: c.maxKhz
                Text(
                    "${c.name} (cores ${c.cpus.trim()}) · ${mhz(top)} of ${mhz(c.maxKhz)}",
                    color = ShelfTheme.colors.textPrimary, fontSize = 12.sp
                )
                if (custom) {
                    // Steps through the speeds this cluster really has (like ClusterTune's list).
                    val idx = c.freqs.indexOfLast { it <= top }.coerceAtLeast(0)
                    ChipFlow {
                        Chip("−", accent = false, enabled = idx > 0) {
                            onChange(p.copy(underclockKhz = p.underclockKhz + (c.name to c.freqs[(idx - 1).coerceIn(0, c.freqs.lastIndex)])))
                        }
                        Chip("+", accent = false, enabled = top < c.maxKhz) {
                            val next = c.freqs.firstOrNull { it > top } ?: c.maxKhz
                            // Back at the top: no ceiling at all for this cluster (a "ceiling" at full speed
                            // would still switch the vendor's boosts off).
                            onChange(p.copy(underclockKhz = if (next >= c.maxKhz) p.underclockKhz - c.name else p.underclockKhz + (c.name to next)))
                        }
                        Chip("Full speed", accent = false, enabled = top < c.maxKhz) {
                            onChange(p.copy(underclockKhz = p.underclockKhz - c.name))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            h?.gpu?.let { g ->
                val lvl = Tuner.underclockGpuLevel(p, g)
                val topMhz = lvl?.let { g.levelMhz.getOrNull(it) } ?: (g.levelMhz.firstOrNull() ?: 0)
                Text("GPU · $topMhz of ${g.levelMhz.firstOrNull() ?: 0} MHz", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp)
                if (custom) {
                    // levelMhz is fastest first: one step slower = next index.
                    val i = g.levelMhz.indexOfFirst { it <= topMhz }.coerceAtLeast(0)
                    ChipFlow {
                        Chip("−", accent = false, enabled = i < g.slowest) {
                            onChange(p.copy(underclockGpuMhz = g.levelMhz[(i + 1).coerceAtMost(g.slowest)]))
                        }
                        Chip("+", accent = false, enabled = i > 0) {
                            onChange(p.copy(underclockGpuMhz = if (i - 1 <= 0) 0 else g.levelMhz[i - 1]))
                        }
                        Chip("Full speed", accent = false, enabled = p.underclockGpuMhz > 0) {
                            onChange(p.copy(underclockGpuMhz = 0))
                        }
                    }
                }
            }
            if (custom && !p.underclocked) MutedNote("Nothing lowered yet: use − on a cluster or the GPU.")
        }
    }
}

/** "Balanced · AutoTDP Smooth · Underclock Medium · Fan Quiet · Xbox buttons". */
private fun profileSummary(p: TuneProfile): String = listOfNotNull(
    p.tier.label,
    "AutoTDP ${p.autoMode.label}".takeIf { p.autoTdp },
    "Underclock ${p.effectiveUnderclock.label}".takeIf { p.underclocked },
    "Fan ${p.fan.label}".takeIf { p.fan != FanMode.SYSTEM },
    when (p.buttonLayout) { "XBOX" -> "Xbox buttons"; "NINTENDO" -> "Retro buttons"; else -> null },
    "${p.refreshHz} Hz".takeIf { p.refreshHz > 0 }
).joinToString(" · ")

/**
 * The three things most often set per game, without opening the whole tuner: underclock, fan and the
 * button layout (Xbox / Retro). Set when the game starts and put back when you return to Voltcap.
 */
@Composable
private fun GameQuickPicks(p: TuneProfile, hasOwn: Boolean, forConsole: String?, onChange: (TuneProfile) -> Unit) {
    val hw = rememberTuneHw()
    ShelfCard {
        SectionLabel(if (forConsole != null) "For all $forConsole games" else "For this game")
        MutedNote(
            when {
                hasOwn -> "Set when the game starts, put back when you return to Voltcap."
                forConsole != null -> "Shows the device-wide settings. Picking anything here makes a $forConsole profile."
                else -> "Shows the settings it uses now. Picking anything here gives this game its own profile."
            }
        )
        Spacer(Modifier.height(6.dp))
        Text("Underclock", color = ShelfTheme.textMuted, fontSize = 12.sp)
        ChipFlow {
            com.shelf.launcher.device.tune.Underclock.values()
                .filter { it != com.shelf.launcher.device.tune.Underclock.CUSTOM || p.underclockKhz.isNotEmpty() || p.underclockGpuMhz > 0 }
                .forEach { u -> Chip(u.label, accent = p.underclock == u) { onChange(p.copy(underclock = u)) } }
        }
        Spacer(Modifier.height(6.dp))
        Text("Fan", color = ShelfTheme.textMuted, fontSize = 12.sp)
        ChipFlow {
            FanMode.values().filter { hw?.fan == true || (it != FanMode.CURVE && it != FanMode.HOLD_TEMP) }.forEach { f ->
                Chip(f.label, accent = p.fan == f) { onChange(p.copy(fan = f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Buttons", color = ShelfTheme.textMuted, fontSize = 12.sp)
        ChipFlow {
            Chip("Unchanged", accent = p.buttonLayout == null) { onChange(p.copy(buttonLayout = null)) }
            Chip("Xbox", accent = p.buttonLayout == "XBOX") { onChange(p.copy(buttonLayout = "XBOX")) }
            Chip("Retro", accent = p.buttonLayout == "NINTENDO") { onChange(p.copy(buttonLayout = "NINTENDO")) }
        }
        MutedNote("Buttons: the handheld's own Xbox / Retro switch (AYN firmware), changed back afterwards.")
    }
}

/**
 * A game's (or app's) performance profile, inside its game settings. For ROM games it also edits the
 * console's profile ("All PS1 games"), which every game of that console without its own profile uses
 * (Settings › Performance › Profiles per console). [siblings]: the other games of that console, for
 * "Copy to all".
 */
@Composable
internal fun GamePerformanceProfile(
    gameKey: String,
    gameName: String,
    platform: String? = null,
    consoleName: String? = null,
    siblings: () -> List<String> = { emptyList() }
) {
    val context = LocalContext.current
    val store = remember { TuneStore(context) }
    val platKey = platform?.takeIf { it.isNotBlank() }?.let { "plat:$it" }
    val console = consoleName ?: platform.orEmpty()
    var own by remember(gameKey) { mutableStateOf(store.forKey(gameKey)) }
    var cons by remember(platKey) { mutableStateOf(platKey?.let { store.forKey(it) }) }
    var editConsole by remember(gameKey) { mutableStateOf(false) }
    var copyArmed by remember(gameKey) { mutableStateOf(false) }
    var copied by remember(gameKey) { mutableStateOf<String?>(null) }
    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
    androidx.compose.runtime.LaunchedEffect(sync) {
        if (sync > 0) { own = store.forKey(gameKey); cons = platKey?.let { store.forKey(it) } }
    }
    if (!Tuner.supported) return
    val consoleOn = platKey != null && store.consoleProfiles
    val consoleMode = editConsole && consoleOn

    fun saveOwn(np: TuneProfile?) { own = np; store.setForKey(gameKey, np); Tuner.profileChanged(context, gameKey) }
    fun saveCons(np: TuneProfile?) { val k = platKey ?: return; cons = np; store.setForKey(k, np); Tuner.profileChanged(context, k) }

    ShelfCard {
        SectionLabel("Performance profile")
        MutedNote(
            when {
                own != null -> "Uses its own profile: " + profileSummary(own!!)
                consoleOn && cons != null -> "Uses the $console profile: " + profileSummary(cons!!)
                else -> "Uses the device-wide settings: " + profileSummary(store.global())
            }
        )
        if (consoleOn) {
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                Chip("This game", accent = !consoleMode) { editConsole = false }
                Chip("All $console games", accent = consoleMode) { editConsole = true }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (consoleMode) {
            ChipFlow {
                Chip("No $console profile", accent = cons == null) { saveCons(null) }
                Chip("$console profile", accent = cons != null) { if (cons == null) saveCons(store.global()) }
            }
            MutedNote("Every $console game without its own profile uses it. Games with their own profile keep theirs.")
        } else {
            ChipFlow {
                Chip(if (consoleOn && cons != null) "$console profile" else "Device-wide", accent = own == null) { saveOwn(null) }
                Chip("Own profile", accent = own != null) { if (own == null) saveOwn(cons.takeIf { consoleOn } ?: store.global()) }
            }
            val learned = remember(gameKey) { store.learnedLevel(gameKey) }
            if (learned != null) MutedNote("AutoTDP learned ${(learned * 100).toInt()} % for $gameName.")
            // Copy this game's profile to the rest of its console.
            if (own != null && platKey != null) {
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    Chip(if (copyArmed) "Tap again: replace their profiles" else "Copy to all $console games", accent = copyArmed) {
                        if (!copyArmed) { copyArmed = true; copied = null }
                        else {
                            val keys = siblings()
                            // Nothing to apply now: a copied profile takes effect when one of those games starts.
                            own?.let { p -> store.setForKeys(keys, p) }
                            copyArmed = false
                            copied = "Copied to ${keys.size} other $console game${if (keys.size == 1) "" else "s"}."
                        }
                    }
                    if (store.consoleProfiles) {
                        Chip("Make it the $console profile", accent = false) {
                            own?.let { saveCons(it) }
                            copied = "Now the $console profile too."
                        }
                    }
                }
                copied?.let { MutedNote(it) }
            }
        }
    }
    val editing = if (consoleMode) cons else own
    val shown = editing ?: if (consoleMode) store.global() else (cons.takeIf { consoleOn } ?: store.global())
    val save: (TuneProfile) -> Unit = { np -> if (consoleMode) saveCons(np) else saveOwn(np) }
    // Quick picks: underclock, fan and buttons in one tap. Picking one gives the game (or the console) its
    // own profile with that change, so nothing else has to be set first.
    Spacer(Modifier.height(8.dp))
    GameQuickPicks(shown, hasOwn = editing != null, forConsole = if (consoleMode) console else null, onChange = save)
    editing?.let { p ->
        Spacer(Modifier.height(8.dp))
        ShelfCard {
            SectionLabel(if (consoleMode) "While a $console game runs" else "While this game runs")
            MutedNote("Set when the game starts and put back when you return to Voltcap.")
            val rates = remember { com.shelf.launcher.device.DeviceControls.refreshRates(context).map { it.toInt() } }
            if (rates.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text("Screen refresh rate", color = ShelfTheme.textMuted, fontSize = 12.sp)
                ChipFlow {
                    Chip("Unchanged", accent = p.refreshHz == 0) { save(p.copy(refreshHz = 0)) }
                    rates.forEach { hz -> Chip("$hz Hz", accent = p.refreshHz == hz) { save(p.copy(refreshHz = hz)) } }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("80 % charge limit", color = ShelfTheme.textMuted, fontSize = 12.sp)
            ChipFlow {
                Chip("Unchanged", accent = p.chargeLimit == null) { save(p.copy(chargeLimit = null)) }
                Chip("On", accent = p.chargeLimit == true) { save(p.copy(chargeLimit = true)) }
                Chip("Off", accent = p.chargeLimit == false) { save(p.copy(chargeLimit = false)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column { TunerEditor(p, null, save) }
    }
    if (!consoleMode && store.measure) {
        Spacer(Modifier.height(8.dp))
        GameMeasurements(gameKey, store)
    }
}

/** What each profile cost while playing this game: battery per hour, watts, temperature, frame rate. */
@Composable
private fun GameMeasurements(gameKey: String, store: TuneStore) {
    var version by remember(gameKey) { mutableStateOf(0) }
    val stats = remember(gameKey, version) { store.stats(gameKey) }
    ShelfCard {
        SectionLabel("Measured")
        if (stats.isEmpty()) {
            MutedNote("Nothing yet. Play this game for a few minutes and what its profile costs shows here (battery per hour, watts, temperature, frame rate).")
        } else {
        MutedNote("Per profile, over every session of 3 minutes or more. Battery per hour counts time on battery only.")
        stats.forEach { (label, st) ->
            Spacer(Modifier.height(6.dp))
            Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val hours = st.minutes / 60f
            val parts = listOfNotNull(
                "${st.sessions} session${if (st.sessions == 1) "" else "s"}, " + (if (hours >= 1f) "%.1f h".format(hours) else "${st.minutes.toInt()} min"),
                st.drainPerHour?.let { "−%.0f %%/h".format(it) },
                st.avgW?.let { "%.1f W".format(it) },
                st.avgTemp?.let { "${it.toInt()} °C" },
                st.avgFps?.let { "${it.toInt()} fps" }
            )
            MutedNote(parts.joinToString(" · "))
        }
        Spacer(Modifier.height(6.dp))
        ChipFlow { Chip("Clear", accent = false) { store.clearStats(gameKey); version++ } }
        }
    }
}



/**
 * What the chosen mode really sets on this handheld, line by line (like Pulse / ClusterTune show it):
 * each CPU cluster's top speed and floor, the prime core's boost, the GPU's top speed and floor, the
 * governor and what it does, the vendor boosts, the power / TDP behaviour and the fan. Worked out the
 * same way the tuner applies it (snapped to speeds the chips really have).
 */
@Composable
private fun TierBreakdown(p: TuneProfile, h: TuneHw?) {
    val rows = buildList<Pair<String, String>> {
        if (p.tier == PowerTier.OFF && !p.autoTdp && !p.underclocked) {
            add("Everything" to "Left to the device: its own clocks, governor, boosts and fan.")
            return@buildList
        }
        val scale = p.powerTarget.coerceIn(30, 100) / 100f
        val clusters = h?.clusters.orEmpty()
        if (h == null) add("CPU" to "Reading the processor…")
        clusters.forEachIndexed { i, c ->
            val base = if (p.tier == PowerTier.CUSTOM) (p.clusterCaps[c.name] ?: 100) else p.tier.cpu
            var pct = base * scale
            if (i == clusters.lastIndex && clusters.size > 1) pct = minOf(pct, p.primeBoost.toFloat())
            var top = if (pct >= 99.5f) c.maxKhz else TuneHardware.snap(c, (c.maxKhz * pct / 100f).toInt())
            Tuner.underclockKhz(p, c, i, clusters.size)?.let { top = minOf(top, it) }
            val floor = if (p.cpuFloor > 0) mhz(TuneHardware.snap(c, (c.maxKhz * p.cpuFloor / 100f).toInt())) else "device's own (${mhz(c.minKhz)})"
            val share = (top * 100L / c.maxKhz.coerceAtLeast(1)).toInt()
            add("CPU ${c.name} (cores ${c.cpus.trim()})" to "Up to ${mhz(top)} of ${mhz(c.maxKhz)} ($share %) · floor $floor")
        }
        if (clusters.size >= 3 && p.primeBoost < 100) add("Prime boost" to "Fastest core held to ${p.primeBoost} % of its top speed")
        h?.gpu?.let { g ->
            val base = if (p.tier == PowerTier.CUSTOM) p.gpuCap else p.tier.gpu
            val pct = (base * (if (p.cpuOnly) 1f else scale)).toInt()
            val topMhz = g.levelMhz.getOrNull(maxOf(TuneHardware.gpuLevelFor(g, pct), Tuner.underclockGpuLevel(p, g) ?: 0)) ?: 0
            val maxMhz = g.levelMhz.firstOrNull() ?: 0
            val floor = if (p.gpuFloor > 0) "${g.levelMhz.getOrNull(TuneHardware.gpuLevelFor(g, p.gpuFloor)) ?: 0} MHz" else "device's own"
            add("GPU" to "Up to $topMhz MHz of $maxMhz MHz · floor $floor" + if (p.cpuOnly) " (CPU only: GPU at its tier speed)" else "")
        }
        val gov = if (p.tier == PowerTier.CUSTOM || p.tier == PowerTier.OFF) p.governor else p.tier.governor
        add("Governor" to when (gov) {
            GovernorChoice.SYSTEM -> "The device's own"
            GovernorChoice.PERFORMANCE -> if (p.autoTdp) "Balanced (AutoTDP needs room to step down)" else "Performance: every core sits at its top speed above — fastest, warmest"
            GovernorChoice.BALANCED -> "Balanced (walt / schedutil): speeds follow the load, up to the limits above"
            GovernorChoice.POWERSAVE -> "Power save: cores stay low and climb slowly — coolest, longest battery"
        })
        add("Touch / launch boosts" to "Switched off while a limit is on, so the limits above really hold (put back after)")
        add("Power (TDP)" to if (p.autoTdp) "AutoTDP ${p.autoMode.label}: lowers clocks while the game holds its frame rate, staying under about ${p.autoMode.ceilingW.toInt()} W"
            else "No fixed wattage on Snapdragon handhelds: the clock limits above are what cap power and heat")
        add("Fan" to when (p.fan) {
            FanMode.SYSTEM -> "The device's own setting"
            FanMode.CURVE -> "Voltcap's curve: " + p.fanCurve.joinToString(" · ") { "${it.tempC} °C → ${it.percent} %" }
            FanMode.HOLD_TEMP -> "As little as needed to hold ${p.holdTempC} °C"
            else -> "The handheld's ${p.fan.label.lowercase()} mode"
        })
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(com.shelf.launcher.ui.theme.Ds.rMedium))
            .background(ShelfTheme.colors.textPrimary.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Text("What ${p.tier.label} changes", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        rows.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k, color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(132.dp))
                Text(v, color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}
