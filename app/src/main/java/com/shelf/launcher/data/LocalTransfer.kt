package com.shelf.launcher.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Settings from one Voltcap to another on the same Wi-Fi, with a 6-digit PIN (no account needed).
 * The sending device announces itself on the local network (Android's network service discovery) and
 * waits a few minutes for one connection; the receiving one finds it, sends the PIN and gets the
 * settings text, which then goes through the Import Preview like any other import. One transfer,
 * then the sender stops listening. A wrong PIN gets nothing; five wrong PINs end the offer.
 */
object LocalTransfer {
    private const val SERVICE_TYPE = "_voltcap._tcp."
    private const val TIMEOUT_MS = 3 * 60_000L

    fun newPin(): String = (100_000..999_999).random().toString()

    /**
     * Sender: offers this device's settings until one receiver with the right [pin] has them or the time
     * runs out. Returns true when they were sent. Suspends; cancel to stop.
     */
    suspend fun send(
        context: Context,
        pin: String,
        includeAccounts: Boolean = true,
        /** Listening on this port now (for the QR code, which connects straight to it). */
        onReady: (port: Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val text = Backup.exportText(context, "nearby", includeDeviceSetup = false, includeAccounts = includeAccounts).toByteArray()
        ServerSocket(0).use { server ->
            // Stop / leaving the screen cancels this: closing the socket ends a waiting accept() at once, so the
            // offer really stops (it used to keep listening, and handing the settings out, for 3 minutes) (update 91).
            val closer = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { runCatching { server.close() } } }
            // Taken inside the try, so nothing that fails on the way can leave the multicast lock held (update 91).
            var multicast: android.net.wifi.WifiManager.MulticastLock? = null
            var reg: NsdManager.RegistrationListener? = null
            try {
                multicast = multicastLock(context)
                server.soTimeout = TIMEOUT_MS.toInt()
                val info = NsdServiceInfo().apply {
                    serviceName = "Voltcap ${android.os.Build.MODEL}".take(60)
                    serviceType = SERVICE_TYPE
                    port = server.localPort
                }
                val listener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(i: NsdServiceInfo) {}
                    override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
                    override fun onServiceUnregistered(i: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
                }
                nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
                reg = listener
                withContext(Dispatchers.Main) { onReady(server.localPort) }
                val deadline = System.currentTimeMillis() + TIMEOUT_MS
                // A few wrong PINs and the offer ends: the settings can carry your sign-ins, and a 6-digit PIN
                // shouldn't be guessable by something else on the Wi-Fi trying again and again.
                var wrong = 0
                while (isActive && System.currentTimeMillis() < deadline && wrong < 5) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use { s ->
                        s.soTimeout = 15_000
                        val input = DataInputStream(s.getInputStream())
                        val out = DataOutputStream(s.getOutputStream())
                        val got = runCatching { input.readUTF() }.getOrNull()
                        // Stopped while this one was connecting: nothing is handed out (update 91).
                        if (got == pin && isActive) {
                            out.writeInt(text.size); out.write(text); out.flush()
                            return@withContext true
                        } else { wrong++; runCatching { out.writeInt(-1); out.flush() } }
                    }
                }
                false
            } finally {
                closer.cancel()
                reg?.let { r -> runCatching { nsd.unregisterService(r) } }
                runCatching { multicast?.release() }
            }
        }
    }

    /** A Voltcap offering its settings nearby. */
    data class Offer(val name: String, val info: NsdServiceInfo)

    /** Receiver: looks for sending devices for [forMs] and returns what it found. */
    suspend fun discover(context: Context, forMs: Long = 6_000L): List<Offer> {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = java.util.concurrent.CopyOnWriteArrayList<Offer>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(i: NsdServiceInfo) { if (found.none { it.name == i.serviceName }) found += Offer(i.serviceName, i) }
            override fun onServiceLost(i: NsdServiceInfo) { found.removeAll { it.name == i.serviceName } }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        // The multicast lock is taken inside the try, so a failing discoverServices can't leave it held (update 91).
        var multicast: android.net.wifi.WifiManager.MulticastLock? = null
        var discovering = false
        try {
            multicast = multicastLock(context)
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            discovering = true
            kotlinx.coroutines.delay(forMs)
        } finally {
            if (discovering) runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { multicast?.release() }
        }
        return found.toList()
    }

    /** Receiver: gets the settings from [offer] with [pin]; the text is a backup for the Import Preview. */
    @Suppress("DEPRECATION")
    suspend fun receive(context: Context, offer: Offer, pin: String): String {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val resolved = withTimeout(10_000) {
            suspendCancellableCoroutine<NsdServiceInfo> { cont ->
                nsd.resolveService(offer.info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("Couldn't reach ${offer.name}."))
                    }
                    override fun onServiceResolved(i: NsdServiceInfo) { if (cont.isActive) cont.resume(i) }
                })
            }
        }
        return fetch(resolved.host.hostAddress ?: error("Couldn't reach ${offer.name}."), resolved.port, pin)
    }

    /**
     * Receiver, straight to an address (from a QR code or typed in): no network discovery needed, so it
     * also works on Wi-Fi that blocks it.
     */
    suspend fun fetch(host: String, port: Int, pin: String): String = withContext(Dispatchers.IO) {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 10_000)
            s.soTimeout = 30_000
            val out = DataOutputStream(s.getOutputStream())
            out.writeUTF(pin.trim()); out.flush()
            val input = DataInputStream(s.getInputStream())
            val n = input.readInt()
            if (n < 0) error("Wrong PIN. Check the 6 digits on the other device.")
            require(n < 20_000_000) { "That didn't look like Voltcap settings." }
            val buf = ByteArray(n)
            input.readFully(buf)
            String(buf)
        }
    }

    /** This device's address on the local network (IPv4), for the QR code; null when not connected. */
    fun localAddress(context: Context): String? = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val lp = cm?.getLinkProperties(cm.activeNetwork)
        lp?.linkAddresses?.map { it.address }?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
            ?: java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun multicastLock(context: Context): android.net.wifi.WifiManager.MulticastLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wifi.createMulticastLock("voltcap-transfer").apply { setReferenceCounted(false); acquire() }
    }.getOrNull()
}
