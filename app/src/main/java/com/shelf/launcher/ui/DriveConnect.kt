package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.data.CloudSync
import kotlinx.coroutines.launch

/**
 * Update 144: "connect Google Drive, then do this" for places outside Settings › Sync (first-start setup, Backup's
 * Export to Google Drive). Returns a function taking what to run once Drive answers; Google's own sign-in screen shows
 * when it's needed (first time on this device). [onProblem] gets what to tell the player when it didn't work.
 */
@Composable
internal fun rememberDriveConnect(onProblem: (String) -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val problem = rememberUpdatedState(onProblem)
    val waiting = remember { arrayOfNulls<() -> Unit>(1) }
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch {
            val error = CloudSync.finishConnect(context, result.data)
            val then = waiting[0]
            waiting[0] = null
            if (error != null) problem.value(error) else then?.invoke()
        }
    }
    return remember(scope) {
        { then: () -> Unit ->
            scope.launch {
                runCatching { CloudSync.connect(context) }
                    .onSuccess { pending ->
                        if (pending != null) {
                            waiting[0] = then
                            signIn.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        } else then()
                    }
                    .onFailure { problem.value(com.shelf.launcher.data.DriveErrors.explain(it)) }
            }
            Unit
        }
    }
}
