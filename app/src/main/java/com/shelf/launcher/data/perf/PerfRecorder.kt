package com.shelf.launcher.data.perf

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Update 321 (Toni: traces from `adb shell perfetto` came out empty on the AYN Thor): Voltcap records its own 15 seconds —
 * Settings › Diagnostics › Performance recording. Three things, written into one zip in Download/Voltcap:
 *  - every frame's time (FrameMetrics: input, animation, layout, drawing, handing to the GPU, the GPU), with percentiles
 *    and the slowest frames — `frames.txt`;
 *  - what the main thread was doing, sampled every 10 ms and added up by stack (the heaviest stacks first; the names are
 *    the release build's short ones — the build's mapping.txt reads them) — `main-thread.txt`;
 *  - Android's own sampling profile of every thread, where the system allows it (`voltcap.trace`, Android Studio opens it).
 * The recording starts a few seconds after it's asked for, so Settings can be closed and the slow screen used meanwhile.
 */
object PerfRecorder {
    const val SECONDS = 15
    private const val DELAY_S = 5

    /** What's happening, for the card: null idle, else a short line ("Starts in 3 s…", "Recording… 9 s left", "Saved …"). */
    var status by mutableStateOf<String?>(null)
        private set

    @Volatile private var running = false
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Starts a recording of [activity]'s window in [DELAY_S] seconds. */
    fun start(activity: Activity) {
        if (running) return
        running = true
        val app = activity.applicationContext
        countdown(DELAY_S) { record(app, activity.window) }
    }

    private fun countdown(s: Int, then: () -> Unit) {
        if (s <= 0) { then(); return }
        status = "Starts in $s s — close Settings and use the slow screen"
        main.postDelayed({ countdown(s - 1, then) }, 1000L)
    }

    private class Frame(val total: Long, val input: Long, val anim: Long, val layout: Long, val draw: Long, val sync: Long, val issue: Long, val gpu: Long)

    private fun record(app: Context, window: Window) {
        val frames = java.util.Collections.synchronizedList(ArrayList<Frame>())
        val dir = File(app.cacheDir, "perf_record").apply { deleteRecursively(); mkdirs() }
        val trace = File(dir, "voltcap.trace")
        // FrameMetrics (Android 7+): each frame's parts, on a thread of its own.
        val thread = android.os.HandlerThread("PerfRecorder").apply { start() }
        val listener = if (Build.VERSION.SDK_INT >= 24) Window.OnFrameMetricsAvailableListener { _, m, _ ->
            fun g(id: Int) = runCatching { m.getMetric(id) }.getOrDefault(0L)
            if (g(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
            frames += Frame(
                g(FrameMetrics.TOTAL_DURATION), g(FrameMetrics.INPUT_HANDLING_DURATION), g(FrameMetrics.ANIMATION_DURATION),
                g(FrameMetrics.LAYOUT_MEASURE_DURATION), g(FrameMetrics.DRAW_DURATION), g(FrameMetrics.SYNC_DURATION),
                g(FrameMetrics.COMMAND_ISSUE_DURATION), if (Build.VERSION.SDK_INT >= 31) g(FrameMetrics.GPU_DURATION) else 0L
            )
        } else null
        if (listener != null) runCatching { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        // Android's sampling profile (every thread, every millisecond) — not allowed on every system: then without it.
        val traced = runCatching { android.os.Debug.startMethodTracingSampling(trace.absolutePath, 48 * 1024 * 1024, 1000) }.isSuccess
        // The main thread's stack every 10 ms, added up.
        val stacks = HashMap<String, Int>()
        var samples = 0
        val mainThread = Looper.getMainLooper().thread
        val sampler = Thread {
            val end = SystemClock.uptimeMillis() + SECONDS * 1000L
            while (SystemClock.uptimeMillis() < end) {
                val st = mainThread.stackTrace
                if (st.isNotEmpty()) {
                    val key = st.take(14).joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                    synchronized(stacks) { stacks[key] = (stacks[key] ?: 0) + 1; samples++ }
                }
                Thread.sleep(10L)
            }
        }.apply { name = "PerfRecorderSampler"; start() }
        var left = SECONDS
        fun tick() {
            if (left <= 0) {
                if (listener != null) runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
                if (traced) runCatching { android.os.Debug.stopMethodTracing() }
                Thread {
                    runCatching { sampler.join(2000L) }
                    thread.quitSafely()
                    val name = save(app, dir, ArrayList(frames), synchronized(stacks) { HashMap(stacks) }, samples, if (traced && trace.length() > 0) trace else null)
                    main.post {
                        status = if (name != null) "Saved Download/Voltcap/$name — upload it" else "Couldn't save the recording"
                        running = false
                    }
                }.start()
                return
            }
            status = "Recording… $left s left"
            left--
            main.postDelayed({ tick() }, 1000L)
        }
        tick()
    }

    private fun ms(ns: Long) = ns / 1_000_000.0

    private fun summary(frames: List<Frame>): String = buildString {
        appendLine("Voltcap ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Frames: ${frames.size} in $SECONDS s")
        if (frames.isEmpty()) { appendLine("(none — nothing was drawn, or this Android has no frame metrics)"); return@buildString }
        fun pct(sel: (Frame) -> Long, p: Double): Double {
            val v = frames.map(sel).sorted()
            return ms(v[((v.size - 1) * p).toInt().coerceIn(0, v.size - 1)])
        }
        val parts = listOf<Pair<String, (Frame) -> Long>>(
            "total" to { it.total }, "input" to { it.input }, "animation" to { it.anim }, "layout" to { it.layout },
            "draw (record)" to { it.draw }, "sync" to { it.sync }, "issue to GPU" to { it.issue }, "GPU" to { it.gpu }
        )
        appendLine(String.format("%-14s %8s %8s %8s %8s", "part (ms)", "p50", "p90", "p99", "max"))
        parts.forEach { (n, sel) ->
            appendLine(String.format("%-14s %8.1f %8.1f %8.1f %8.1f", n, pct(sel, 0.5), pct(sel, 0.9), pct(sel, 0.99), ms(frames.maxOf(sel))))
        }
        val slow = frames.count { it.total > 16_700_000L }
        appendLine("Over 16.7 ms: $slow (${slow * 100 / frames.size} %)")
        appendLine()
        appendLine("Slowest 20 frames (ms: total | input anim layout draw sync issue gpu):")
        frames.sortedByDescending { it.total }.take(20).forEach { f ->
            appendLine(String.format("%7.1f | %5.1f %5.1f %5.1f %5.1f %5.1f %5.1f %5.1f", ms(f.total), ms(f.input), ms(f.anim), ms(f.layout), ms(f.draw), ms(f.sync), ms(f.issue), ms(f.gpu)))
        }
    }

    private fun stackText(stacks: Map<String, Int>, samples: Int): String = buildString {
        appendLine("Main thread, sampled every 10 ms: $samples samples. Heaviest stacks first (share of the time).")
        appendLine("Idle (waiting for the next frame) shows as MessageQueue.nativePollOnce.")
        appendLine()
        stacks.entries.sortedByDescending { it.value }.take(40).forEach { (k, n) ->
            appendLine("${n * 100.0 / samples.coerceAtLeast(1)} %  ($n)")
            appendLine(k)
            appendLine()
        }
    }

    /** The zip into Download/Voltcap (MediaStore, Android 10+; the app's own files folder before). Its name, or null. */
    private fun save(app: Context, dir: File, frames: List<Frame>, stacks: Map<String, Int>, samples: Int, trace: File?): String? = runCatching {
        val name = "voltcap-perf-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".zip"
        fun write(out: java.io.OutputStream) {
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("frames.txt")); z.write(summary(frames).toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("main-thread.txt")); z.write(stackText(stacks, samples).toByteArray()); z.closeEntry()
                if (trace != null) { z.putNextEntry(ZipEntry("voltcap.trace")); trace.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Voltcap")
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
            app.contentResolver.openOutputStream(uri)?.use { write(it) } ?: return@runCatching null
        } else {
            val out = File(app.getExternalFilesDir(null), name)
            out.outputStream().use { write(it) }
        }
        dir.deleteRecursively()
        name
    }.getOrNull()
}
