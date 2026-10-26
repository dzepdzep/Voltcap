package com.shelf.launcher.ui

import androidx.activity.enableEdgeToEdge
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.RaOverlayData
import com.shelf.launcher.ui.theme.ShelfLauncherTheme
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * The in-game Quick Menu: your achievements for the running game and your saved Bluetooth headsets and
 * controllers (connect / disconnect), over the game, without quitting it. Opened from its Quick Settings
 * tile and from the in-game panel. Tap outside, Done or B closes it.
 */
class QuickMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.shelf.launcher.CrashGuard.install(this)
        super.onCreate(savedInstanceState)
        // Update 136: drawn edge to edge like Voltcap's home, with the bars hidden from the start. Without it Android
        // painted a black navigation-bar strip along the bottom while this window was open, even with the bar hidden.
        enableEdgeToEdge()
        runCatching { SystemBars.apply(window) }
        Features.init(this)
        val theme = runCatching { com.shelf.launcher.data.ThemeStore(this).load() }.getOrNull()
        val appearance = runCatching { com.shelf.launcher.data.AppearanceStore(this).load() }.getOrNull()
        setContent {
            if (theme != null && appearance != null) ShelfLauncherTheme(appTheme = theme, appearance = appearance) { Menu { finish() } }
            else ShelfLauncherTheme { Menu { finish() } }
        }
    }

    // Update 130: Hide navigation bar applies to this window too (it used to bring the bar back while open).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) runCatching { SystemBars.apply(window) }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Composable
    private fun Menu(onDone: () -> Unit) {
        val ctx = this
        var tab by remember { mutableIntStateOf(0) }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { onDone() } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).padding(16.dp)
                    .voltSurface(uiRounded(18.dp), base = ShelfTheme.colors.surface, alpha = 0.97f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Quick menu", color = ShelfTheme.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    Chip("Achievements", accent = tab == 0) { tab = 0 }
                    Chip("Bluetooth", accent = tab == 1) { tab = 1 }
                }
                Spacer(Modifier.height(10.dp))
                if (tab == 0) AchievementsTab() else BluetoothTab(ctx)
                Spacer(Modifier.height(12.dp))
                ChipFlow {
                    Chip("Done", accent = true) { onDone() }
                    // (Update 334: change who's playing from a game — Voltcap opens on its profiles; the game keeps running.)
                    Chip("Who's playing…", accent = false) {
                        runCatching {
                            ctx.startActivity(
                                Intent(ctx, com.shelf.launcher.MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    .putExtra(ProfilePicker.EXTRA, true)
                            )
                        }
                        onDone()
                    }
                }
            }
        }
    }

    @Composable
    private fun AchievementsTab() {
        val st by RaOverlayData.state.collectAsState()
        val s = st
        if (s == null) {
            MutedNote("No achievements to show: either the game running now isn't linked to RetroAchievements, or no game is running through Voltcap.")
            return
        }
        Text(s.gameTitle, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${s.unlocked} of ${s.total} unlocked · ${s.points} / ${s.totalPoints} points", color = ShelfTheme.textMuted, fontSize = 12.sp)
        s.lastUnlock?.let {
            Spacer(Modifier.height(6.dp))
            Text("Last unlock: $it", color = ShelfTheme.accent, fontSize = 12.sp)
        }
        if (s.nextTitle != null) {
            Spacer(Modifier.height(10.dp))
            Text("NEXT", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            AchievementLine(s.nextBadge, s.nextTitle, s.nextDescription ?: "", s.nextWhy)
        }
        if (s.missablesLeft.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("MISSABLE", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            s.missablesLeft.take(6).forEach { Text("• $it", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (s.left.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("STILL TO GET (${s.left.size})", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            s.left.take(12).forEach { AchievementLine(it.badge, it.title, it.description ?: "", if (it.missable) "Missable" else it.percent?.let { p -> "${p.toInt()} % of players" }) }
        }
        if (s.unlockedList.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("UNLOCKED", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            s.unlockedList.take(8).forEach { AchievementLine(it.badge, it.title, it.description ?: "", it.earned) }
        }
    }

    @Composable
    private fun AchievementLine(badge: String?, title: String, description: String, note: String?) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                AsyncImage(model = badge, contentDescription = null, modifier = Modifier.padding(end = 10.dp).widthIn(max = 40.dp).height(40.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (description.isNotBlank()) Text(description, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!note.isNullOrBlank()) Text(note, color = ShelfTheme.textMuted.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Bluetooth: saved (bonded) headsets and controllers
    // ------------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    @Composable
    private fun BluetoothTab(ctx: Context) {
        var granted by remember { mutableStateOf(hasBluetoothPermission(ctx)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        var tick by remember { mutableIntStateOf(0) }
        var message by remember { mutableStateOf<String?>(null) }
        val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
        val connected = remember { mutableStateOf<Set<String>>(emptySet()) }

        if (adapter == null) { MutedNote("This device has no Bluetooth."); return }
        if (!granted) {
            MutedNote("Voltcap needs Android's permission to see your paired Bluetooth devices.")
            Chip("Allow", accent = true) { if (Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            return
        }
        // Which of them are connected right now: ask the audio and headset profiles (and input devices, when Android lets us).
        DisposableEffect(tick) {
            val found = mutableSetOf<String>()
            val proxies = mutableListOf<Pair<Int, BluetoothProfile>>()
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            // A proxy that arrives after this effect is gone is closed at once, not leaked (update 91).
            var disposed = false
            fun listener(profile: Int) = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    if (disposed) { runCatching { adapter.closeProfileProxy(p, proxy) }; return }
                    proxies += p to proxy
                    runCatching { proxy.connectedDevices.forEach { found += it.address } }
                    handler.post { connected.value = found.toSet() }
                }
                override fun onServiceDisconnected(p: Int) {}
            }
            // 1 = headset, 2 = A2DP, 4 = input device (hidden id; refused on some Android versions).
            listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, 4).forEach { id -> runCatching { adapter.getProfileProxy(ctx, listener(id), id) } }
            onDispose {
                disposed = true
                proxies.forEach { (id, proxy) -> runCatching { adapter.closeProfileProxy(id, proxy) } }
            }
        }
        val devices = remember(tick, granted) {
            runCatching { adapter.bondedDevices.orEmpty().sortedBy { it.name ?: it.address } }.getOrDefault(emptyList())
        }
        if (!adapter.isEnabled) {
            MutedNote("Bluetooth is off. Switch it on from Android's quick settings, then come back.")
        }
        if (devices.isEmpty()) MutedNote("No paired devices yet. Pair one in Android's Bluetooth settings.")
        devices.forEach { d ->
            val on = d.address in connected.value
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(runCatching { d.name }.getOrNull() ?: d.address, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(kindOf(d) + if (on) " · connected" else "", color = if (on) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 11.sp)
                }
                Chip(if (on) "Disconnect" else "Connect", accent = !on) {
                    message = "${if (on) "Disconnecting" else "Connecting"}…"
                    Thread {
                        val ok = setConnected(ctx, adapter, d, !on)
                        runOnUiThread {
                            message = if (ok) null else "Android didn't let Voltcap do that for ${runCatching { d.name }.getOrNull() ?: "this device"}. Opening Bluetooth settings."
                            if (!ok) runCatching { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            tick++
                        }
                    }.start()
                }
            }
        }
        message?.let { Spacer(Modifier.height(6.dp)); MutedNote(it) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Refresh", accent = false) { tick++ }
            Chip("Bluetooth settings", accent = false) {
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun hasBluetoothPermission(ctx: Context) =
        Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun kindOf(d: BluetoothDevice): String = when (runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()) {
        android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "Headset / speaker"
        android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "Controller / keyboard / mouse"
        android.bluetooth.BluetoothClass.Device.Major.PHONE -> "Phone"
        android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "Computer"
        else -> "Bluetooth device"
    }

    /**
     * Connects / disconnects through the profile services. Android hides these calls from apps, so this
     * goes through reflection and may be refused; the caller then opens Android's Bluetooth settings.
     */
    @SuppressLint("MissingPermission", "PrivateApi", "DiscouragedPrivateApi")
    private fun setConnected(ctx: Context, adapter: BluetoothAdapter, device: BluetoothDevice, connect: Boolean): Boolean {
        val latch = java.util.concurrent.CountDownLatch(3)
        var any = false
        val proxies = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, BluetoothProfile>>())
        fun attempt(id: Int) {
            val ok = runCatching {
                adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        proxies += p to proxy
                        val done = runCatching {
                            val m = proxy.javaClass.getMethod(if (connect) "connect" else "disconnect", BluetoothDevice::class.java)
                            m.isAccessible = true
                            (m.invoke(proxy, device) as? Boolean) == true
                        }.getOrDefault(false)
                        if (done) any = true
                        latch.countDown()
                    }
                    override fun onServiceDisconnected(p: Int) {}
                }, id)
            }.getOrDefault(false)
            if (!ok) latch.countDown()
        }
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, 4).forEach { attempt(it) }
        latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
        proxies.forEach { (id, proxy) -> runCatching { adapter.closeProfileProxy(id, proxy) } }
        return any
    }
}
