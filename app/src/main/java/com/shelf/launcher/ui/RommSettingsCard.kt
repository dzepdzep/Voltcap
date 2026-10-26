package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Romm
import com.shelf.launcher.data.RommCredentials
import com.shelf.launcher.data.RommCredentialsStore
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/** Settings › Accounts › RomM (update 93): your RomM server, then "Browse RomM" to download games from it. */
@Composable
internal fun RommSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { RommCredentialsStore(context) }
    val saved = remember { store.load() }
    var server by remember { mutableStateOf(saved?.server.orEmpty()) }
    var token by remember { mutableStateOf(saved?.token.orEmpty()) }
    var user by remember { mutableStateOf(saved?.user.orEmpty()) }
    var pass by remember { mutableStateOf(saved?.password.orEmpty()) }
    var status by remember { mutableStateOf(if (saved != null) "Saved." else null) }
    var connected by remember { mutableStateOf(saved != null) }
    // Update 336: a pairing code instead of typing a long token.
    var code by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(saved != null && saved.token.isBlank() && saved.user.isNotBlank()) }
    ShelfCard {
        MutedNote(
            "Browse your own RomM server's library and download games straight into your ROM folders (each into its console's " +
                "folder; the library updates when it's done). Downloads keep going with the screen off."
        )
        Spacer(Modifier.height(8.dp))
        ShelfTextField(server, { server = it }, "Server address (e.g. http://192.168.1.20:8080)", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        // Update 336: the easy way — RomM 5's pairing code (8 letters and digits).
        ShelfTextField(code, { code = it.uppercase().take(9) }, "Pairing code (e.g. K7MP-Q2XA)", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Chip("Pair", enabled = server.isNotBlank() && code.replace("-", "").trim().length >= 8) {
            status = "Pairing…"
            scope.launch {
                runCatching { Romm.exchangePairCode(server, code) }
                    .onSuccess { t ->
                        token = t; user = ""; pass = ""; code = ""
                        val c = RommCredentials(server, t, "", "")
                        val err = Romm.test(c)
                        if (err == null) { store.save(c); connected = true; status = "Paired — connected to RomM." }
                        else { store.save(c); connected = true; status = "Paired. (Test: $err)" }
                    }
                    .onFailure { status = it.message ?: "Couldn't pair." }
            }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote("RomM in a browser › Settings › Client API Tokens › make one (read is enough) › Pair: type the code it shows here.")
        Spacer(Modifier.height(8.dp))
        Chip(if (manual) "Hide the token / password fields" else "Use a token or a password instead…", accent = false) { manual = !manual }
        if (manual) {
            Spacer(Modifier.height(8.dp))
            ShelfTextField(token, { token = it }, "Client API token (rmm_…)", password = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            MutedNote("Or, instead of a token, your RomM user name and password:")
            ShelfTextField(user, { user = it }, "User name", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            ShelfTextField(pass, { pass = it }, "Password", password = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        status?.let { Text(it, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(6.dp)) }
        ChipFlow {
            if (manual) Chip("Save & test", enabled = server.isNotBlank() && (token.isNotBlank() || user.isNotBlank())) {
                val c = RommCredentials(server, token, user, pass)
                status = "Connecting…"
                scope.launch {
                    val err = Romm.test(c)
                    if (err == null) { store.save(c); connected = true; status = "Connected to RomM." }
                    else status = err
                }
            }
            if (connected) Chip("Browse RomM", accent = false) { RommUi.showing = true }
            if (connected) Chip("Sign out", accent = false) {
                store.clear(); connected = false; token = ""; pass = ""; status = "Signed out."
            }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(
            "A server at http:// in your home network works too (your PC's address and RomM's port, e.g. :8090). " +
                "Downloads need \"Access to all files\" (Settings › Permissions) to land in your ROM folder."
        )
    }
}
