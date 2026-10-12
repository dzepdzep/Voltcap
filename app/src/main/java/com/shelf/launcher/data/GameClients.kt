package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/** Public DroidDeck 0.3.1 launch contract; an ID is data, never a command or guest path. */
object GameClients {
    const val DROIDDECK = "com.droiddeck.launcher"
    const val PXPLAY = "psplay.grill.com"
    const val ASOBI = "com.asobi.droid"
    val clients = listOf("DroidDeck" to DROIDDECK, "PXPlay" to PXPLAY, "Asobi" to ASOBI)

    fun droidDeckId(text: String): String? {
        val value = text.trim()
        val id = if (value.startsWith("droiddeck://game/")) value.removePrefix("droiddeck://game/") else value
        if (!Regex("[1-9][0-9]{0,19}").matches(id)) return null
        return id.takeIf { it.toULongOrNull() != null }
    }

    fun launchDroidDeck(context: Context, path: String, options: android.os.Bundle?): LaunchOutcome {
        val id = runCatching {
            File(path).inputStream().use { input ->
                val bytes = ByteArray(129)
                var count = 0
                while (count < bytes.size) {
                    val n = input.read(bytes, count, bytes.size - count)
                    if (n <= 0) break
                    count += n
                }
                if (count > 128) null else droidDeckId(String(bytes, 0, count, Charsets.UTF_8))
            }
        }.getOrNull() ?: return LaunchOutcome.Error("This DroidDeck launch file is invalid. Export it again from DroidDeck.")
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("droiddeck://game/$id")).apply {
                setPackage(DROIDDECK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }, options)
            LaunchOutcome.Launched
        } catch (_: android.content.ActivityNotFoundException) {
            LaunchOutcome.EmulatorNotFound(DROIDDECK)
        } catch (e: Exception) {
            LaunchOutcome.Error("Couldn't open DroidDeck: ${e.message ?: "Android refused the launch"}")
        }
    }
}
