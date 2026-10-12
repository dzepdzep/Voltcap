package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * QR transfer (Settings › Backup › Share settings › QR code): the sending Voltcap shows a QR code with
 * its address, port and the one-time PIN; the receiving one scans it (or a phone's camera opens the
 * link in Voltcap) and connects straight to it — the same transfer as the PIN one ([LocalTransfer]),
 * without typing or network discovery. The settings still open in the Import Preview first.
 */
object QrTransfer {
    data class Target(val host: String, val port: Int, val pin: String, val name: String) {
        /** Typed instead of scanned (devices without a camera): "address:port". */
        val shortCode: String get() = "$host:$port"
    }

    /** A link that arrived from outside (a phone camera opening the QR), waiting for Settings › Backup. */
    val pending = MutableStateFlow<Target?>(null)

    fun link(t: Target): String = Uri.Builder().scheme("voltcap").authority("transfer")
        .appendQueryParameter("h", t.host)
        .appendQueryParameter("p", t.port.toString())
        .appendQueryParameter("k", t.pin)
        .appendQueryParameter("n", t.name.take(40))
        .build().toString()

    /** What a scanned / opened QR code points at, or null if it isn't a Voltcap transfer code. */
    fun parse(text: String?): Target? = runCatching {
        val u = Uri.parse(text?.trim() ?: return null)
        if (u.scheme != "voltcap" || u.host != "transfer") return null
        val host = u.getQueryParameter("h")?.takeIf { it.isNotBlank() } ?: return null
        val port = u.getQueryParameter("p")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val pin = u.getQueryParameter("k")?.filter(Char::isDigit)?.takeIf { it.length == 6 } ?: return null
        Target(host, port, pin, u.getQueryParameter("n").orEmpty().ifBlank { "Voltcap" })
    }.getOrNull()

    /** "192.168.1.20:41234" typed in, with the PIN typed separately. */
    fun parseShort(code: String, pin: String): Target? {
        val c = code.trim()
        val host = c.substringBeforeLast(':', "").takeIf { it.isNotBlank() } ?: return null
        val port = c.substringAfterLast(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val p = pin.filter(Char::isDigit).takeIf { it.length == 6 } ?: return null
        return Target(host, port, p, host)
    }

    /** MainActivity: a voltcap://transfer link opened Voltcap. True if it was one. */
    fun fromIntent(intent: Intent?): Boolean {
        val t = parse(intent?.dataString) ?: return false
        pending.value = t
        return true
    }

    /**
     * Scans a QR code with Google Play services' scanner (its own camera screen: Voltcap needs no
     * camera permission). [onResult] gets the text, or null when cancelled / not available
     * (handhelds without a camera or Play services: type the short code instead).
     */
    fun scan(context: Context, onResult: (String?) -> Unit) {
        runCatching {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { onResult(it.rawValue) }
                .addOnFailureListener { onResult(null) }
                .addOnCanceledListener { onResult(null) }
        }.onFailure { onResult(null) }
    }

    /** Whether this device has a camera at all (the Scan button is hidden otherwise). */
    fun hasCamera(context: Context): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
}
