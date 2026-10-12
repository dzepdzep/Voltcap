package com.shelf.launcher.spotify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Receives voltcap://spotify-callback after the user approves in the browser,
 * hands it to [SpotifyAuth] (which checks `state` and swaps the code for
 * tokens in the background) and immediately returns to Voltcap. No UI.
 */
class SpotifyAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        // voltcap://spotify-callback?done=1 is just "come back" from the sign-in page (the real redirect
        // already went to the loopback receiver); only a link carrying a result is passed on.
        i?.data?.takeIf { it.getQueryParameter("state") != null || it.getQueryParameter("code") != null }
            ?.let { SpotifyAuth.get(this).handleCallback(it) }
        // MainActivity is singleTask: this brings the existing one forward.
        startActivity(Intent(this, com.shelf.launcher.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
