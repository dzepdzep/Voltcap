package com.shelf.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.spotify.SpotifyConnection
import com.shelf.launcher.spotify.SpotifyRepository
import com.shelf.launcher.spotify.SpotifyResult
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/** Settings › Accounts: connect your own Spotify developer app (Client ID + PKCE, no secret). */
@Composable
internal fun SpotifySettingsCard() {
    val context = LocalContext.current
    val repo = remember { SpotifyRepository(context) }
    val scope = rememberCoroutineScope()
    val connection by repo.connection.collectAsState()
    val error by repo.authError.collectAsState()
    var clientId by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<String?>(null) }
    var premium by remember { mutableStateOf<Boolean?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { clientId = repo.clientId() }
    LaunchedEffect(connection) {
        if (connection == SpotifyConnection.CONNECTED) {
            (repo.me() as? SpotifyResult.Ok)?.value?.let { account = it.display_name ?: it.id; premium = it.isPremium() }
        } else { account = null; premium = null }
    }

    SettingsHeading("Spotify")
    ShelfCard {
        Text(
            when (connection) {
                SpotifyConnection.CONNECTED -> "Connected" + (account?.let { " as $it" } ?: "") +
                    when (premium) { true -> " · Premium"; false -> " · Free (browse only)"; null -> "" }
                SpotifyConnection.CONNECTING -> "Waiting for Spotify in your browser…"
                SpotifyConnection.NEEDS_RECONNECT -> "Spotify needs to be reconnected."
                SpotifyConnection.NO_CLIENT_ID -> "Not set up yet — paste your Client ID below."
                SpotifyConnection.DISCONNECTED -> "Not connected."
            },
            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = ShelfTheme.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        ShelfTextField(
            value = clientId,
            onValueChange = { clientId = it },
            label = "Spotify Client ID",
            onSubmit = { scope.launch { repo.setClientId(clientId) } }
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel("Redirect URI (add exactly this in your Spotify app)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(repo.redirectUri, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            Chip(if (copied) "Copied" else "Copy", accent = false) {
                runCatching {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Spotify redirect URI", repo.redirectUri))
                    copied = true
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            val connected = connection == SpotifyConnection.CONNECTED
            if (connected) {
                Chip("Disconnect", accent = false) { scope.launch { repo.disconnect() } }
            } else {
                Chip(
                    when (connection) { SpotifyConnection.NEEDS_RECONNECT -> "Reconnect"; SpotifyConnection.CONNECTING -> "Try again"; else -> "Connect Spotify" },
                    enabled = clientId.isNotBlank()
                ) {
                    scope.launch {
                        repo.setClientId(clientId)
                        repo.connect(context)
                    }
                }
                if (connection == SpotifyConnection.CONNECTING) Chip("Cancel", accent = false) { repo.cancelConnect() }
            }
        }
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            androidx.compose.material.icons.Icons.Filled.PlayArrow, "Play without opening Spotify",
            "Picking a song while nothing plays: Spotify wakes up in the background on this handheld and plays it there. Off: you pick a device or open the app.",
            com.shelf.launcher.data.Features.spotifyQuietWake.state.value,
            onChange = { com.shelf.launcher.data.Features.spotifyQuietWake.value = it }
        )
        Spacer(Modifier.height(8.dp))
        MutedNote(
            "Optional. Music controls, the now-playing card and pausing Voltcap's music work with any music app " +
                "without this — it only adds browsing your Spotify library inside Voltcap."
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "How to set it up (once, about 5 minutes):\n" +
                "1. On a phone or PC, open developer.spotify.com/dashboard and sign in.\n" +
                "2. Create app: any name and description, tick \"Web API\", and as Redirect URI paste exactly the one above.\n" +
                "3. Open the app's Settings, copy the Client ID, paste it here, then tap Connect.\n" +
                "4. Sign in to Spotify in the page that opens and agree.",
            color = ShelfTheme.colors.textPrimary.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp
        )
        Spacer(Modifier.height(6.dp))
        MutedNote(
            "Spotify's rules (since February 2026): the account that creates the developer app needs Premium, and the " +
                "app works for at most 5 people, each added by e-mail under the app's \"User Management\". So everyone " +
                "uses their own app. Voltcap never sees a password or client secret. Controlling playback needs Premium; " +
                "anything can always be opened in the Spotify app."
        )
    }
}
