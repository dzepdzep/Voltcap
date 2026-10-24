package com.shelf.launcher.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.crash.CrashReports
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Returns a function that saves a crash report through the system file picker
 * (Storage Access Framework): `.txt` when the report is a single file, `.zip`
 * when it also has a log / exit info. Safe to call from any thread; tells the
 * user with a toast how it went. Used by the crash screen, the safe-mode banner
 * and [CrashReportsCard].
 */
@Composable
fun rememberCrashExport(): (String) -> Unit {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var pending by remember { mutableStateOf<CrashReports.Export?>(null) }

    fun say(text: String) {
        main.post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
    }

    val onPicked: (Uri?) -> Unit = { uri ->
        val export = pending
        pending = null
        if (uri != null && export != null) {
            Thread {
                val ok = runCatching {
                    (context.contentResolver.openOutputStream(uri, "w") ?: error("no stream")).use { export.write(it) }
                }.isSuccess
                say(if (ok) "Crash report saved." else "Could not save the crash report.")
            }.start()
        }
    }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), onPicked)
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), onPicked)

    return remember(context) {
        { id: String ->
            Thread {
                val export = CrashReports.prepareExport(context.applicationContext, id)
                if (export == null) {
                    say("That crash report is no longer available.")
                } else {
                    main.post {
                        pending = export
                        runCatching {
                            if (export.mime == "application/zip") saveZip.launch(export.fileName) else saveText.launch(export.fileName)
                        }.onFailure { say("No file picker available to save the report.") }
                    }
                }
            }.start()
        }
    }
}

/**
 * Settings card: the last few saved crash reports with Export and Delete.
 * Shows a short "none" note when there are no reports.
 *
 * Wiring (architect): in SettingsDialog, wherever the diagnostics/about cards
 * are listed, add `CrashReportsCard()`.
 */
@Composable
fun CrashReportsCard() {
    com.shelf.launcher.Breadcrumbs.mark("CrashReportsCard")
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<CrashReports.Entry>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        entries = withContext(Dispatchers.IO) { CrashReports.list(context.applicationContext) }
    }
    val export = rememberCrashExport()
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    ShelfCard {
        SectionLabel("Crash reports")
        if (entries.isEmpty()) {
            MutedNote("No crash reports saved. If Voltcap closes unexpectedly, the report is kept here so you can send it.")
        } else {
            MutedNote("The last ${CrashReports.MAX_REPORTS} are kept. Reports never include your settings, keys or passwords.")
            entries.forEach { entry ->
                Spacer(Modifier.height(10.dp))
                Text(format.format(Date(entry.timeMillis)), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
                Text(entry.summary, color = ShelfTheme.textMuted, fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    Chip("Export") { export(entry.id) }
                    Chip("Delete", accent = false) {
                        Thread {
                            CrashReports.delete(context.applicationContext, entry.id)
                            Handler(Looper.getMainLooper()).post { reload++ }
                        }.start()
                    }
                }
            }
        }
    }
}

/**
 * Update 321: Settings card — Performance recording (data/perf/PerfRecorder): "Record 15 s" starts in a few seconds,
 * so Settings can be closed and the slow screen used; the zip lands in Download/Voltcap.
 */
@Composable
fun PerfRecordCard() {
    val context = LocalContext.current
    val status = com.shelf.launcher.data.perf.PerfRecorder.status
    ShelfCard {
        SectionLabel("Performance recording")
        MutedNote(
            "Records ${com.shelf.launcher.data.perf.PerfRecorder.SECONDS} seconds of how long each frame takes and what Voltcap was busy with. " +
                "It starts 5 seconds after you press it: close Settings and use the screen that's slow (scroll the carousel, say). " +
                "The file is saved in Download/Voltcap — send it for a look."
        )
        Spacer(Modifier.height(6.dp))
        if (status != null) Text(status, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Record ${com.shelf.launcher.data.perf.PerfRecorder.SECONDS} s") {
                var c: android.content.Context? = context
                while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
                (c as? android.app.Activity)?.let { com.shelf.launcher.data.perf.PerfRecorder.start(it) }
            }
        }
    }
}
