package com.shelf.launcher.device.accessories

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.core.content.ContextCompat
import com.shelf.launcher.device.DeviceProfiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

enum class AccessoryKind { CONTROLLER, HEADPHONES, EARBUDS, KEYBOARD, MOUSE, SPEAKER, OTHER }
enum class AccessoryTransport { BLUETOOTH, USB, WIRELESS_DONGLE, UNKNOWN }

/**
 * Every field is null when the source didn't report it - never a guess.
 * [source] is a short human note ("reported by Android" / "reported by the controller").
 */
data class AccessoryBattery(
    val levelPercent: Int?,
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val charging: Boolean? = null,
    val source: String
) {
    /** Lowest known level (earbuds show their weaker bud). */
    val headline: Int? get() = levelPercent ?: listOfNotNull(left, right).minOrNull()
}

data class Accessory(
    val id: String,
    val name: String,
    val kind: AccessoryKind,
    val transport: AccessoryTransport,
    val battery: AccessoryBattery?,
    /** Stays the same across reconnects (the input descriptor / Bluetooth address): what "Hide" remembers. */
    val hideKey: String = id,
    /** Looks like the handheld's own built-in controls (not a separate pad). */
    val builtIn: Boolean = false
)

/**
 * Accessories hidden from Connected devices and the status bar: ones picked by hand, and (on by
 * default) the handheld's own controls, which many handhelds report as a connected pad — AYN's show up
 * as an "Xbox Wireless Controller".
 */
object HiddenAccessories {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("accessories", Context.MODE_PRIVATE)
    fun hidden(c: Context): Set<String> = prefs(c).getStringSet("hidden", emptySet()).orEmpty()
    fun setHidden(c: Context, key: String, hide: Boolean) {
        val now = hidden(c).toMutableSet(); if (hide) now += key else now -= key
        prefs(c).edit().putStringSet("hidden", now).apply()
    }
    fun hideBuiltIn(c: Context): Boolean = prefs(c).getBoolean("hide_builtin", true)
    fun setHideBuiltIn(c: Context, on: Boolean) = prefs(c).edit().putBoolean("hide_builtin", on).apply()
    fun isHidden(c: Context, a: Accessory): Boolean = a.hideKey in hidden(c) || (a.builtIn && hideBuiltIn(c))
}

/**
 * Connected controllers (InputDevice) and Bluetooth peripherals with real battery levels.
 * Event driven: listeners only exist between [start] and [stop]; the one timer is a
 * 5-minute re-read, because battery changes don't raise device-changed events everywhere.
 * Use [acquire]/[release] so the status bar and the popup share one running instance.
 */
class AccessoryMonitor private constructor(private val app: Context) {
    private val _state = MutableStateFlow<List<Accessory>>(emptyList())
    val state: StateFlow<List<Accessory>> = _state

    private val main = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var refs = 0
    private var started = false

    private var inputListener: InputManager.InputDeviceListener? = null
    private var btReceiver: BroadcastReceiver? = null
    private var adapter: BluetoothAdapter? = null
    private val proxies = HashMap<Int, BluetoothProfile>()
    /** Profiles asked for and not answered yet: a refresh meanwhile mustn't ask again (each answer is a proxy to close). */
    private val requested = HashSet<Int>()
    private val proxyLock = Any()

    fun acquire() { if (++refs == 1) start() }
    fun release() { if (refs > 0 && --refs == 0) stop() }

    /** Call after the Bluetooth permission was granted or Bluetooth was toggled. */
    fun refresh() {
        if (!started) return
        ensureBluetooth()
        val ex = executor ?: return
        runCatching { ex.execute { runCatching { _state.value = collect() } } }
    }

    private val periodic = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, REREAD_MS)
        }
    }
    private val debounced = Runnable { refresh() }
    private fun refreshSoon(delayMs: Long = 400) {
        main.removeCallbacks(debounced)
        main.postDelayed(debounced, delayMs)
    }

    private fun start() {
        if (started) return
        started = true
        executor = Executors.newSingleThreadExecutor()
        runCatching {
            val im = app.getSystemService(Context.INPUT_SERVICE) as InputManager
            val l = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = refreshSoon()
                override fun onInputDeviceRemoved(deviceId: Int) = refreshSoon()
                override fun onInputDeviceChanged(deviceId: Int) = refreshSoon()
            }
            im.registerInputDeviceListener(l, main)
            inputListener = l
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                // Hidden (system) broadcast; harmless if this Android never delivers it to apps.
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
            }
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) closeProxies()
                    // Profile lists update a moment after the broadcast.
                    refreshSoon(if (intent?.action == ACTION_BATTERY_LEVEL_CHANGED) 300 else 1200)
                }
            }
            // System broadcasts from the Bluetooth process: must be exported to arrive.
            ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_EXPORTED)
            btReceiver = r
        }
        main.removeCallbacks(periodic)
        periodic.run()
    }

    private fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(periodic)
        main.removeCallbacks(debounced)
        runCatching {
            inputListener?.let { (app.getSystemService(Context.INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(it) }
        }
        inputListener = null
        runCatching { btReceiver?.let { app.unregisterReceiver(it) } }
        btReceiver = null
        closeProxies()
        adapter = null
        executor?.shutdown()
        executor = null
    }

    // ---- Bluetooth profile proxies ---------------------------------------------------------------

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            synchronized(proxyLock) {
                requested -= profile
                if (!started) { runCatching { adapter?.closeProfileProxy(profile, proxy) }; return }
                // A second answer for a profile already held: keep the first, close this one.
                if (proxies.containsKey(profile)) { runCatching { adapter?.closeProfileProxy(profile, proxy) }; return }
                proxies[profile] = proxy
            }
            refreshSoon(100)
        }
        override fun onServiceDisconnected(profile: Int) {
            synchronized(proxyLock) { proxies.remove(profile) }
            refreshSoon(100)
        }
    }

    private fun ensureBluetooth() {
        if (!hasBluetoothPermission(app)) { closeProxies(); return }
        val a = adapter ?: runCatching {
            (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        }.getOrNull()?.also { adapter = it }
        if (a == null || !runCatching { a.isEnabled }.getOrDefault(false)) { closeProxies(); return }
        synchronized(proxyLock) {
            for (profile in intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
                if (proxies.containsKey(profile) || profile in requested) continue
                if (runCatching { a.getProfileProxy(app, serviceListener, profile) }.getOrDefault(false)) requested += profile
            }
        }
    }

    private fun closeProxies() {
        // Copy and clear first: closeProfileProxy calls onServiceDisconnected right away on this
        // thread, which removes from the map — iterating it meanwhile crashed (ConcurrentModification)
        // whenever Voltcap went to the background.
        val a = adapter
        val toClose = synchronized(proxyLock) { requested.clear(); proxies.toList().also { proxies.clear() } }
        toClose.forEach { (profile, proxy) -> runCatching { a?.closeProfileProxy(profile, proxy) } }
    }

    // ---- Collecting ---------------------------------------------------------------------------------

    private fun collect(): List<Accessory> {
        val controllers = collectControllers()
        val bt = collectBluetooth()
        // A Bluetooth gamepad shows up in both lists: keep one row, marked Bluetooth.
        val merged = ArrayList<Accessory>()
        val btUsed = HashSet<String>()
        for (c in controllers) {
            // Never the handheld's own controls: on AYN they share the name "Xbox Wireless Controller"
            // with a real Xbox pad, which would otherwise merge into (and unhide) the built-in entry.
            val twin = if (c.builtIn) null
            else bt.firstOrNull { it.kind == AccessoryKind.CONTROLLER && it.name.equals(c.name, true) && it.id !in btUsed }
            if (twin != null) {
                btUsed += twin.id
                merged += c.copy(transport = AccessoryTransport.BLUETOOTH, battery = c.battery ?: twin.battery, builtIn = false)
            } else merged += c
        }
        merged += bt.filter { it.id !in btUsed }
        val vendor = runCatching { DeviceProfiles.current(app).accessoryBatteries(app) }.getOrDefault(emptyList())
        val ids = merged.map { it.id }.toSet()
        merged += vendor.filter { it.id !in ids }
        return merged
    }

    private fun collectControllers(): List<Accessory> {
        // Without isExternal (API 29) the built-in handheld controls can't be told apart from
        // real gamepads, so nothing is listed rather than listing the handheld itself.
        if (Build.VERSION.SDK_INT < 29) return emptyList()
        val usb = runCatching {
            (app.getSystemService(Context.USB_SERVICE) as UsbManager).deviceList.values.map { it.vendorId to it.productId }.toSet()
        }.getOrDefault(emptySet())
        val out = ArrayList<Accessory>()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            val pad = d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if (!pad || d.isVirtual || !d.isExternal) continue
            val transport = if (d.vendorId != 0 && (d.vendorId to d.productId) in usb) AccessoryTransport.USB else AccessoryTransport.UNKNOWN
            out += Accessory(
                "input:${d.id}", d.name ?: "Controller", AccessoryKind.CONTROLLER, transport, controllerBattery(d),
                hideKey = "pad:" + (d.descriptor ?: "${d.vendorId}:${d.productId}:${d.name}"),
                builtIn = transport != AccessoryTransport.USB && looksBuiltIn(d)
            )
        }
        return out
    }

    /**
     * The handheld's own controls: on a handheld maker's device, a pad without a battery of its own that
     * is named like the built-in one (AYN reports "Xbox Wireless Controller"; others a "gamepad" / gpio).
     * A real Bluetooth pad merged in later is marked Bluetooth and never counts as built in.
     */
    private fun looksBuiltIn(d: InputDevice): Boolean {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        val handheld = listOf("ayn", "retroid", "anbernic", "ayaneo", "odin", "gpd", "powkiddy", "miyoo").any { it in maker }
        if (!handheld) return false
        val n = (d.name ?: "").lowercase()
        val noBattery = Build.VERSION.SDK_INT < 31 || runCatching { !d.batteryState.isPresent }.getOrDefault(true)
        return noBattery && ("xbox wireless controller" in n || "gamepad" in n || "gpio" in n || "odin" in n || "retroid" in n || "anbernic" in n)
    }

    private fun controllerBattery(d: InputDevice): AccessoryBattery? {
        if (Build.VERSION.SDK_INT < 31) return null
        val b = runCatching { d.batteryState }.getOrNull() ?: return null
        if (!b.isPresent) return null
        val cap = b.capacity
        val pct = if (cap.isNaN() || cap < 0f || cap > 1f) null else (cap * 100f).roundToInt()
        val charging = when (b.status) {
            BatteryState.STATUS_CHARGING -> true
            BatteryState.STATUS_DISCHARGING -> false
            else -> null
        }
        if (pct == null && charging == null) return null
        return AccessoryBattery(pct, charging = charging, source = "reported by the controller")
    }

    private fun collectBluetooth(): List<Accessory> {
        if (!hasBluetoothPermission(app)) return emptyList()
        val a = adapter ?: return emptyList()
        val devices = LinkedHashMap<String, BluetoothDevice>()
        runCatching {
            synchronized(proxyLock) { proxies.values.toList() }.forEach { p ->
                p.connectedDevices.forEach { devices[it.address] = it }
            }
        }
        // Keyboards, mice and gamepads aren't A2DP/HSP profiles: ask bonded devices whether they're
        // connected (hidden method; if this Android hides it they're simply not listed).
        runCatching {
            a.bondedDevices.orEmpty().forEach { if (it.address !in devices && isConnected(it)) devices[it.address] = it }
        }
        return devices.values.mapNotNull { runCatching { toAccessory(it) }.getOrNull() }
    }

    private fun toAccessory(d: BluetoothDevice): Accessory {
        val name = runCatching { d.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth device"
        val left = metadataPercent(d, META_LEFT)
        val right = metadataPercent(d, META_RIGHT)
        val case = metadataPercent(d, META_CASE)
        val untethered = runCatching { metadataString(d, META_UNTETHERED)?.equals("true", true) }.getOrNull() == true
        val overall = batteryLevel(d)
        var kind = kindOf(d)
        if (untethered || left != null || right != null) kind = AccessoryKind.EARBUDS
        val hasAny = overall != null || left != null || right != null || case != null
        val battery = if (hasAny) AccessoryBattery(overall, left, right, case, null, "reported by Android") else null
        return Accessory("bt:${d.address}", name, kind, AccessoryTransport.BLUETOOTH, battery)
    }

    private fun kindOf(d: BluetoothDevice): AccessoryKind {
        val c = runCatching { d.bluetoothClass }.getOrNull() ?: return AccessoryKind.OTHER
        val dc = c.deviceClass
        return when (c.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (dc) {
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> AccessoryKind.HEADPHONES
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> AccessoryKind.SPEAKER
                else -> AccessoryKind.OTHER
            }
            BluetoothClass.Device.Major.PERIPHERAL -> when {
                dc and 0x40 != 0 -> AccessoryKind.KEYBOARD
                dc and 0x80 != 0 -> AccessoryKind.MOUSE
                (dc and 0x3C) == 0x04 || (dc and 0x3C) == 0x08 -> AccessoryKind.CONTROLLER // joystick / gamepad minor
                else -> AccessoryKind.OTHER
            }
            else -> AccessoryKind.OTHER
        }
    }

    // ---- Hidden Bluetooth API via reflection (all failures mean "not reported") -----------------

    private fun isConnected(d: BluetoothDevice): Boolean = runCatching {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as? Boolean
    }.getOrNull() == true

    /** 0..100 or null; the platform returns -1 when unknown. */
    private fun batteryLevel(d: BluetoothDevice): Int? = runCatching {
        (BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as? Int)?.takeIf { it in 0..100 }
    }.getOrNull()

    private fun metadataString(d: BluetoothDevice, key: Int): String? = try {
        val bytes = BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType).invoke(d, key) as? ByteArray
        bytes?.let { String(it, Charsets.UTF_8) }
    } catch (_: Throwable) {
        // Usually SecurityException (needs BLUETOOTH_PRIVILEGED): leave null.
        null
    }

    private fun metadataPercent(d: BluetoothDevice, key: Int): Int? =
        metadataString(d, key)?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }

    companion object {
        private const val REREAD_MS = 5 * 60 * 1000L
        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val META_UNTETHERED = 6
        private const val META_LEFT = 10
        private const val META_RIGHT = 11
        private const val META_CASE = 12

        @Volatile private var instance: AccessoryMonitor? = null
        fun get(context: Context): AccessoryMonitor =
            instance ?: synchronized(this) {
                instance ?: AccessoryMonitor(context.applicationContext).also { instance = it }
            }

        /** Android 12+ asks at runtime; older versions use the install-time BLUETOOTH permission. */
        fun hasBluetoothPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        fun bluetoothAvailable(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }
}
