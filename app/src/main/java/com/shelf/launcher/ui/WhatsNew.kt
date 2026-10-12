package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shelf.launcher.ui.theme.ShelfTheme
import java.util.concurrent.TimeUnit

/**
 * Update 149: after Voltcap is updated, a short "What's new" card with that version's notes from its GitHub release
 * (the text given to tools\release\release.ps1). Once per version; not on a fresh install, not without notes.
 */
internal object WhatsNew {
    /** The version and its notes to show now, or null. */
    val shown = mutableStateOf<Pair<String, String>?>(null)

    fun check(context: android.content.Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("whats_new", android.content.Context.MODE_PRIVATE)
        val now = com.shelf.launcher.data.AppUpdates.installed(app).first
        val last = prefs.getString("seen", null)
        if (last == now) return
        prefs.edit().putString("seen", now).apply()
        // A fresh install has nothing "new" to tell.
        if (last == null) return
        Thread {
            runCatching {
                val client = okhttp3.OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
                val url = "https://api.github.com/repos/${com.shelf.launcher.data.AppUpdates.REPO}/releases/tags/v$now"
                client.newCall(okhttp3.Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val notes = org.json.JSONObject(r.body?.string().orEmpty()).optString("body").trim()
                    if (notes.isNotEmpty()) shown.value = now to notes
                }
            }
        }.apply { isDaemon = true }.start()
    }
}

/** The card itself (shown by the home screen while [WhatsNew.shown] has something). */
@Composable
internal fun WhatsNewDialog(version: String, notes: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        Column(Modifier.widthIn(max = 560.dp)) { ShelfCard {
            SectionLabel("What's new in Voltcap $version")
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                // (Markdown marks from the release notes left out: plain lines read better here.)
                Text(
                    notes.lines().joinToString("\n") { it.trimStart('#', ' ').replace("**", "").replace(Regex("^[-*] "), "•  ") },
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Chip("OK") { onDismiss() }
        } }
    }
}
