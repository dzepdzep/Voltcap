package com.shelf.launcher.ui

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.shelf.launcher.data.perf.PerfConfig
import com.shelf.launcher.data.perf.PerfCorner
import com.shelf.launcher.data.perf.PerfSampler
import com.shelf.launcher.data.perf.PerfSettings
import com.shelf.launcher.data.perf.PerfSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A small always-on-top readout while a game runs. It is a plain TextView in a
 * TYPE_APPLICATION_OVERLAY window that can never take focus, so it cannot steal
 * controller input from the game (touches only on itself: drag, fold arrow, resize). Not a foreground service: an
 * overlay window from a started service is allowed while the app holds the
 * "display over other apps" permission. Started when a game starts, stopped
 * when Voltcap comes back; it samples only while it exists and the screen is on.
 */
class PerfOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var view: TextView? = null
    private var scope: CoroutineScope? = null
    private var acquired = false
    private var receiver: BroadcastReceiver? = null
    private var displayId = android.view.Display.DEFAULT_DISPLAY
    private var musicView: MusicOverlayView? = null
    /** True while the music player overlay is really showing (something playing): others may use its corner otherwise. */
    private val musicVisible = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var raView: RaOverlayView? = null
    /** Overlays under a finger right now: the live updates leave their position alone until let go. */
    private val draggingViews = HashSet<View>()
    /** Each overlay's window (update 121): the overlay inside its fold arrow / resize frame. */
    private val shells = HashMap<View, OverlayShell>()
    /** The window an overlay sits in (what WindowManager knows). */
    private fun win(v: View): View = shells[v] ?: v

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's size / opacity buttons: change the setting; every overlay follows live.
        intent?.getStringExtra(EXTRA_ADJUST)?.let { what ->
            PerfSettings.update(this) { c ->
                when (what) {
                    "smaller" -> c.copy(overlayScale = (c.overlayScale - 10).coerceIn(50, 200))
                    "bigger" -> c.copy(overlayScale = (c.overlayScale + 10).coerceIn(50, 200))
                    "fainter" -> c.copy(overlayOpacity = (c.overlayOpacity - 15).coerceIn(20, 100))
                    "solid" -> c.copy(overlayOpacity = (c.overlayOpacity + 15).coerceIn(20, 100))
                    // One button (Android shows three): 100 → 75 → 50 → 30 → back to 100 %.
                    "opacity" -> c.copy(overlayOpacity = when { c.overlayOpacity > 75 -> 75; c.overlayOpacity > 50 -> 50; c.overlayOpacity > 30 -> 30; else -> 100 })
                    else -> c
                }
            }
            if (what == "power" && com.shelf.launcher.device.tune.Tuner.supported) {
                Thread { runCatching { com.shelf.launcher.device.tune.Tuner.cycleTile(applicationContext) }; postNotification() }.start()
            }
            postNotification()
            // Started only for the button (no overlay showing): don't linger as an idle service, which
            // also made a later "overlays on" think the overlay was already running.
            if (view == null && musicView == null && raView == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        val running = view != null || musicView != null || raView != null
        // Overlays switched mid-game (Quick Settings tile): rebuild with the new choice, same screen.
        val reconfigure = intent?.getBooleanExtra(EXTRA_RECONFIGURE, false) == true
        if (reconfigure && !running) return START_NOT_STICKY
        if (running && !reconfigure) return START_NOT_STICKY // already showing
        val wantPerf: Boolean
        val wantMusic: Boolean
        val wantRa: Boolean
        if (reconfigure) {
            teardownViews()
            val c = PerfSettings.config(this)
            wantPerf = c.showDuringGames
            wantMusic = c.musicDuringGames
            wantRa = c.raDuringGames && com.shelf.launcher.data.RaOverlayData.state.value != null
        } else {
            displayId = intent?.getIntExtra(EXTRA_DISPLAY, android.view.Display.DEFAULT_DISPLAY) ?: android.view.Display.DEFAULT_DISPLAY
            wantPerf = intent?.getBooleanExtra(EXTRA_PERF, true) ?: true
            wantMusic = intent?.getBooleanExtra(EXTRA_MUSIC, false) ?: false
            wantRa = intent?.getBooleanExtra(EXTRA_RA, false) ?: false
        }
        // A foreground service, so Android's background limits don't stop the overlay a minute
        // into the game (a plain started service is killed once Voltcap is in the background).
        com.shelf.launcher.data.Features.init(this) // the pinned achievement and overlay options
        if (!goForeground()) { stopSelf(); return START_NOT_STICKY }
        if (!canDraw(this)) { stopSelf(); return START_NOT_STICKY }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        val perfShown = wantPerf && show()
        val musicShown = wantMusic && showMusic()
        val raShown = wantRa && showRa()
        if (!perfShown && !musicShown && !raShown) { stopSelf(); return START_NOT_STICKY }
        if (raShown) {
            // Redrawn only when the achievement data or the settings change (no timer).
            s.launch {
                kotlinx.coroutines.flow.combine(
                    com.shelf.launcher.data.RaOverlayData.state, PerfSettings.state, musicVisible,
                    com.shelf.launcher.data.Features.raPinned.asFlow(), com.shelf.launcher.data.Features.raShowPinned.asFlow()
                ) { st, cfg, mv, _, _ -> Triple(st, cfg, mv) }.collect { (st, cfg, mv) ->
                    val v = raView ?: return@collect
                    if (st == null) { v.visibility = View.GONE; return@collect }
                    v.bind(st, cfg)
                    v.visibility = View.VISIBLE
                    applyCorner(v, cfg.effectiveCorners(mv).third)
                }
            }
        }
        if (perfShown) {
            PerfSampler.setPaused(false)
            PerfSampler.acquire(this)
            acquired = true
            val screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, i: Intent?) {
                    // Screen off: stop reading entirely until it comes back.
                    PerfSampler.setPaused(i?.action == Intent.ACTION_SCREEN_OFF)
                }
            }
            registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            })
            receiver = screenReceiver
            s.launch {
                PerfSettings.config(this@PerfOverlayService)
                kotlinx.coroutines.flow.combine(PerfSampler.snapshot, PerfSettings.state, musicVisible) { snap, cfg, mv -> Triple(snap, cfg, mv) }.collect { (snap, cfg, mv) ->
                    view?.let { v ->
                        v.text = overlayText(cfg, snap)
                        applyCorner(v, cfg.effectiveCorners(mv).first)
                    }
                }
            }
        }
        if (musicShown) {
            runCatching { com.shelf.launcher.data.LyricsOptIn.init(this) }
            s.launch {
                var lyricsKey: String? = null
                var lyrics: com.shelf.launcher.data.Lyrics? = null
                val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                while (true) {
                    val mv = musicView ?: break
                    if (power.isInteractive) {
                        val cfg = PerfSettings.state.value
                        val np = runCatching {
                            com.shelf.launcher.data.ShelfNotificationListener.nowPlaying(this@PerfOverlayService)?.first
                        }.getOrNull()
                        if (np == null || np.title == null) {
                            mv.collapse()
                            mv.visibility = View.GONE
                            musicVisible.value = false
                        } else {
                            musicVisible.value = true
                            val key = "${np.title}|${np.artist}"
                            if (cfg.musicLyrics && key != lyricsKey) {
                                lyricsKey = key
                                lyrics = null
                                lyrics = runCatching {
                                    com.shelf.launcher.data.LyricsRepository.fetch(np.title, np.artist, np.album, np.durationMs)
                                }.getOrNull()
                            }
                            val pos = np.currentPosition()
                            val l = lyrics
                            val line = when {
                                !cfg.musicLyrics || l == null -> null
                                l.isSynced -> l.lineAt(pos)?.ifBlank { "♪" } ?: "♪"
                                else -> l.plain.firstOrNull()
                            }
                            mv.chipButtons = cfg.musicChipButtons
                            mv.bind(np, line)
                            mv.visibility = View.VISIBLE
                            if (mv.interactive != cfg.musicInteractive) {
                                mv.interactive = cfg.musicInteractive
                                if (!cfg.musicInteractive) mv.collapse()
                                updateMusicFlags()
                            }
                            applyCorner(mv, cfg.effectiveCorners(true).second)
                        }
                    }
                    // Battery: wake when the next lyric line starts (timed lyrics), otherwise every 2 s
                    // (song / play state changes only); 5 s while the screen is off.
                    val wait = if (!power.isInteractive) 5_000L else run {
                        val l = lyrics
                        val np2 = runCatching { com.shelf.launcher.data.ShelfNotificationListener.nowPlaying(this@PerfOverlayService)?.first }.getOrNull()
                        if (l != null && l.isSynced && np2 != null && np2.isPlaying && PerfSettings.state.value.musicLyrics) {
                            val pos = np2.currentPosition()
                            val next = l.synced.firstOrNull { it.atMs > pos }?.atMs
                            if (next != null) (next - pos + 30L).coerceIn(250L, 2_000L) else 2_000L
                        } else 2_000L
                    }
                    kotlinx.coroutines.delay(wait)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun show(): Boolean = runCatching {
        var ctx: Context = this
        if (displayId != android.view.Display.DEFAULT_DISPLAY) {
            val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(displayId)
            if (display != null) ctx = createDisplayContext(display)
        }
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val tv = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            val pad = (6 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                setColor(0xB0000000.toInt()); cornerRadius = 8 * resources.displayMetrics.density
            }
            // Never focusable (the controller stays with the game), no a11y noise; touchable only to be
            // dragged to another corner (Overlays › Move by dragging).
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            text = "…"
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            movableFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityFor(PerfSettings.config(this@PerfOverlayService).effectiveCorners().first)
            val m = (8 * resources.displayMetrics.density).toInt()
            x = m; y = m
        }
        wm.addView(wrap(tv, Which.STATS), params)
        windowManager = wm
        view = tv
        makeDraggable(tv, Which.STATS)
        true
    }.getOrDefault(false)

    /**
     * The music window's touch flags. Never focusable (the controller stays with the game).
     * Interaction off: not touchable at all. Collapsed: only the chip takes touches (NOT_TOUCH_MODAL
     * passes everything around it to the game). Open: also told about touches outside, to close.
     */
    private fun musicFlags(interactive: Boolean, expanded: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        // Not interactive: still touchable while overlays may be moved (only to drag it; taps do nothing) or
        // while it has its fold arrow.
        f = if (!interactive && !touchableAnyway(Which.MUSIC)) f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else f or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (interactive && expanded) f = f or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        return f
    }

    private fun updateMusicFlags() {
        val mv = musicView ?: return
        val w = win(mv)
        val lp = w.layoutParams as? WindowManager.LayoutParams ?: return
        val f = musicFlags(mv.interactive, mv.expanded)
        if (lp.flags != f) {
            lp.flags = f
            runCatching { windowManager?.updateViewLayout(w, lp) }
        }
    }

    private fun showMusic(): Boolean = runCatching {
        var ctx: Context = this
        if (displayId != android.view.Display.DEFAULT_DISPLAY) {
            val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(displayId)
            if (display != null) ctx = createDisplayContext(display)
        }
        val wm = windowManager ?: (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        val viewScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        AlbumArtBlur.load(this)
        val interactive = PerfSettings.config(this@PerfOverlayService).musicInteractive
        val mv = MusicOverlayView(ctx, viewScope) { updateMusicFlags() }.apply {
            visibility = View.GONE
            this.interactive = interactive
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            musicFlags(interactive, false),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityFor(PerfSettings.config(this@PerfOverlayService).musicCorner)
            val m = (8 * resources.displayMetrics.density).toInt()
            x = m; y = m
        }
        wm.addView(wrap(mv, Which.MUSIC), params)
        windowManager = wm
        musicView = mv
        makeDraggable(mv, Which.MUSIC)
        true
    }.getOrDefault(false)

    /** The RetroAchievements overlay: a small never-touchable text panel, like the performance one. */
    private fun showRa(): Boolean = runCatching {
        var ctx: Context = this
        if (displayId != android.view.Display.DEFAULT_DISPLAY) {
            val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(displayId)
            if (display != null) ctx = createDisplayContext(display)
        }
        val wm = windowManager ?: (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        val d = resources.displayMetrics.density
        val viewScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        val tv = RaOverlayView(ctx, viewScope) {
            updateRaFlags()
            // Opened / closed: the scaled window follows its new size.
            raView?.let { v -> v.post { applyCorner(v, PerfSettings.state.value.effectiveCorners(musicVisible.value).third) } }
        }.apply {
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            raFlags(false),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityFor(PerfSettings.config(this@PerfOverlayService).effectiveCorners().third)
            val m = (8 * d).toInt()
            x = m; y = m
        }
        wm.addView(wrap(tv, Which.RA), params)
        windowManager = wm
        raView = tv
        makeDraggable(tv, Which.RA)
        true
    }.getOrDefault(false)

    /** Touches the overlays need even without taps of their own: dragging, the fold arrow, a corner resize. */
    // Update 144: also while this overlay is folded — its folded tab still shows and must take the tap that unfolds it
    // (with "Fold arrows" and "Drag to move" both off it used to stay folded for good).
    private fun touchableAnyway(which: Which) = PerfSettings.config(this).overlaysMovable ||
        com.shelf.launcher.data.Features.overlayArrows.value || which.key in com.shelf.launcher.data.Features.overlayFolded.value

    /** Touch flags for the stats chip: touchable (to drag, fold, resize it) only when moving or the arrows are on; never focusable. */
    private fun movableFlags(): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        f = if (touchableAnyway(Which.STATS)) f or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        else f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    /** The achievements panel always takes taps on itself (to open); open, it also hears taps outside (to close). */
    private fun raFlags(expanded: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (expanded) f = f or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        return f
    }

    private fun updateRaFlags() {
        val v = raView ?: return
        val w = win(v)
        val lp = w.layoutParams as? WindowManager.LayoutParams ?: return
        val f = raFlags(v.expanded)
        if (lp.flags != f) { lp.flags = f; runCatching { windowManager?.updateViewLayout(w, lp) } }
    }

    private enum class Which(val key: String) { STATS("stats"), MUSIC("music"), RA("ra") }

    private fun whichOf(v: View): Which = when (v) {
        musicView -> Which.MUSIC
        raView -> Which.RA
        else -> Which.STATS
    }

    /**
     * Drag an overlay with a finger: it follows the finger and stays where you let go. Let go near a
     * corner and it snaps into that corner (whatever was there takes its old place). A plain tap still
     * does what the overlay does on a tap.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(v: View, which: Which) {
        var downX = 0f
        var downY = 0f
        var grabX = 0
        var grabY = 0
        var dragging = false
        val slop = 12 * resources.displayMetrics.density
        val listener = View.OnTouchListener { view, e ->
            if (!PerfSettings.config(this).overlaysMovable) return@OnTouchListener false
            if (which == Which.RA && (v as? RaOverlayView)?.expanded == true) return@OnTouchListener false
            if (which == Which.MUSIC && (v as? MusicOverlayView)?.expanded == true) return@OnTouchListener false
            val w = win(v)
            val lp = w.layoutParams as? WindowManager.LayoutParams ?: return@OnTouchListener false
            when (e.actionMasked) {
                // The stats chip (and a music chip with taps off) isn't clickable: it has to claim the touch
                // to hear the drag. The others are, so they only listen alongside.
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; dragging = false; (v as? RaOverlayView)?.moved = false
                    val loc = IntArray(2); w.getLocationOnScreen(loc)
                    grabX = (e.rawX - loc[0]).toInt(); grabY = (e.rawY - loc[1]).toInt()
                    which == Which.STATS || (which == Which.MUSIC && (v as? MusicOverlayView)?.interactive == false)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (kotlin.math.abs(e.rawX - downX) > slop || kotlin.math.abs(e.rawY - downY) > slop)) {
                        dragging = true; draggingViews += v; (v as? RaOverlayView)?.moved = true; w.alpha = (w.alpha * 0.7f).coerceAtLeast(0.3f)
                        lp.gravity = Gravity.TOP or Gravity.START
                    }
                    if (dragging) {
                        // Follows the finger (window coordinates from the screen's top-left), anywhere on the screen.
                        val (sw, sh) = screenSize(w)
                        val ww = if (lp.width > 0) lp.width else w.width
                        val h = if (lp.height > 0) lp.height else w.height
                        lp.x = (e.rawX.toInt() - grabX).coerceIn(0, (sw - ww).coerceAtLeast(0))
                        lp.y = (e.rawY.toInt() - grabY).coerceIn(0, (sh - h).coerceAtLeast(0))
                        runCatching { windowManager?.updateViewLayout(w, lp) }
                    }
                    dragging
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    draggingViews -= v
                    if (dragging) {
                        val (sw, sh) = screenSize(w)
                        val ww = if (lp.width > 0) lp.width else w.width
                        val h = if (lp.height > 0) lp.height else w.height
                        val roomX = (sw - ww).coerceAtLeast(1)
                        val roomY = (sh - h).coerceAtLeast(1)
                        val fx = lp.x / roomX.toFloat()
                        val fy = lp.y / roomY.toFloat()
                        // Near a corner: snap into it (the old corner behaviour). Anywhere else: stay there.
                        val near = 0.08f
                        val cx = when { fx < near -> 0; fx > 1f - near -> 1; else -> -1 }
                        val cy = when { fy < near -> 0; fy > 1f - near -> 1; else -> -1 }
                        if (cx >= 0 && cy >= 0) {
                            OverlayPositions.clear(which.key)
                            moveTo(which, when {
                                cy == 0 && cx == 0 -> PerfCorner.TOP_LEFT
                                cy == 0 -> PerfCorner.TOP_RIGHT
                                cx == 0 -> PerfCorner.BOTTOM_LEFT
                                else -> PerfCorner.BOTTOM_RIGHT
                            })
                        } else OverlayPositions.set(which.key, fx, fy)
                        // Back to its own opacity, placed by the settings (so a snap lands exactly).
                        view.post { applyCorner(v, cornerOf(which)) }
                        true
                    } else false
                }
                else -> false
            }
        }
        if (v is RaOverlayView) v.getChildAt(0)?.setOnTouchListener(listener) else v.setOnTouchListener(listener)
    }

    /**
     * Update 122: the whole screen [w] is on, the way it's turned now (its display's real size). The service's own
     * display metrics could be another size (the display unturned, or without the bars), which kept a dragged overlay
     * out of part of the screen.
     */
    private fun screenSize(w: View): Pair<Int, Int> {
        val m = android.util.DisplayMetrics()
        val display = w.display ?: runCatching { @Suppress("DEPRECATION") windowManager?.defaultDisplay }.getOrNull()
        @Suppress("DEPRECATION")
        if (display != null) display.getRealMetrics(m) else m.setTo(resources.displayMetrics)
        return m.widthPixels.coerceAtLeast(1) to m.heightPixels.coerceAtLeast(1)
    }

    /** The corner [which] would sit in now (before a free position is applied). */
    private fun cornerOf(which: Which): PerfCorner {
        val (perf, music, ra) = PerfSettings.state.value.effectiveCorners(musicVisible.value)
        return when (which) { Which.STATS -> perf; Which.MUSIC -> music; Which.RA -> ra }
    }

    /** Moves an overlay to [target]; the one already there (if any) swaps into its old corner. */
    private fun moveTo(which: Which, target: PerfCorner) {
        PerfSettings.update(this) { c ->
            val (perf, music, ra) = c.effectiveCorners()
            val from = when (which) { Which.STATS -> perf; Which.MUSIC -> music; Which.RA -> ra }
            var n = c.copy(corner = perf, musicCorner = music, raCorner = ra)
            if (c.showDuringGames && perf == target && which != Which.STATS) n = n.copy(corner = from)
            if (c.musicDuringGames && music == target && which != Which.MUSIC) n = n.copy(musicCorner = from)
            if (c.raDuringGames && ra == target && which != Which.RA) n = n.copy(raCorner = from)
            when (which) {
                Which.STATS -> n.copy(corner = target)
                Which.MUSIC -> n.copy(musicCorner = target)
                Which.RA -> n.copy(raCorner = target)
            }
        }
    }

    /**
     * Puts [content] in its window frame: the fold arrow, the folded tab and the corner resize
     * (update 121). Folded state and own size are remembered per overlay.
     */
    private fun wrap(content: View, which: Which): OverlayShell {
        val sh = OverlayShell(
            content.context, content,
            beforeFold = {
                (content as? MusicOverlayView)?.collapse()
                (content as? RaOverlayView)?.collapse()
            },
            onFoldChanged = { f ->
                val now = com.shelf.launcher.data.Features.overlayFolded.value
                com.shelf.launcher.data.Features.overlayFolded.value = if (f) now + which.key else now - which.key
                // Update 144: folded / unfolded changes whether the window takes touches (see touchableAnyway).
                when (which) {
                    Which.STATS -> {
                        val w = win(content)
                        val lp = w.layoutParams as? WindowManager.LayoutParams
                        val nf = movableFlags()
                        if (lp != null && lp.flags != nf) { lp.flags = nf; runCatching { windowManager?.updateViewLayout(w, lp) } }
                    }
                    Which.MUSIC -> updateMusicFlags()
                    Which.RA -> Unit
                }
                applyCorner(content, cornerOf(which))
            },
            canResize = {
                (content as? MusicOverlayView)?.expanded != true && (content as? RaOverlayView)?.expanded != true
            },
            onResize = { own, done ->
                shells[content]?.ownSize = own
                applyCorner(content, cornerOf(which))
                if (done) {
                    OverlayPositions.setSize(which.key, own)
                    // Update 179: where it ended up (the corner held still) becomes its place.
                    resizeAnchors.remove(content)?.let { saveFreePlace(content, which) }
                }
            }
        )
        // Update 179: a corner drag keeps the opposite corner still (only for an overlay moved to a spot of its own; one in a
        // screen corner already grows out of that corner).
        sh.onResizeStart = { right, bottom ->
            val lp = sh.layoutParams as? WindowManager.LayoutParams
            if (lp != null && OverlayPositions.get(which.key) != null && sh.width > 0)
                resizeAnchors[content] = ResizeAnchor(right, bottom, lp.x, lp.y, sh.width, sh.height)
        }
        sh.ownSize = OverlayPositions.size(which.key)
        sh.arrowsOn = com.shelf.launcher.data.Features.overlayArrows.value
        sh.setFolded(which.key in com.shelf.launcher.data.Features.overlayFolded.value, animate = false, tell = false)
        shells[content] = sh
        return sh
    }

    /** Update 179: the overlay's window when a corner drag began, and which corner. */
    private class ResizeAnchor(val right: Boolean, val bottom: Boolean, val x: Int, val y: Int, val w: Int, val h: Int)
    private val resizeAnchors = HashMap<View, ResizeAnchor>()

    /** Update 179: saves where [v]'s window is now as its free place (fractions of the room left on screen). */
    private fun saveFreePlace(v: View, which: Which) {
        val w = shells[v] ?: v
        val lp = w.layoutParams as? WindowManager.LayoutParams ?: return
        val (sw, sh2) = screenSize(w)
        val fx = lp.x.toFloat() / (sw - w.width).coerceAtLeast(1)
        val fy = lp.y.toFloat() / (sh2 - w.height).coerceAtLeast(1)
        OverlayPositions.set(which.key, fx, fy)
    }

    private fun applyCorner(v: View, corner: PerfCorner) {
        if (v in draggingViews) return
        val sh = shells[v]
        val w = sh ?: v
        val lp = w.layoutParams as? WindowManager.LayoutParams ?: return
        // Dragged to a free spot: placed there from the top-left (after its size is known, below).
        val free = OverlayPositions.get(whichOf(v).key)
        val g = if (free != null) Gravity.TOP or Gravity.START else gravityFor(corner)
        var changed = false
        if (lp.gravity != g) { lp.gravity = g; changed = true }
        val density = resources.displayMetrics.density
        if (free == null) {
            // Folded: the tab sits against the screen's edge, a little in from the side (clear of round corners).
            val folded = sh?.folded == true
            val mx = ((if (folded) 10 else 8) * density).toInt()
            val my = if (folded) 0 else (8 * density).toInt()
            if (lp.x != mx || lp.y != my) { lp.x = mx; lp.y = my; changed = true }
        }
        if (sh != null) {
            // Which way it folds and where its arrow goes: towards the nearer edge.
            sh.atBottom = free?.let { it.second > 0.5f } ?: (corner == PerfCorner.BOTTOM_LEFT || corner == PerfCorner.BOTTOM_RIGHT)
            sh.atEnd = free?.let { it.first > 0.5f } ?: (corner == PerfCorner.TOP_RIGHT || corner == PerfCorner.BOTTOM_RIGHT)
        }
        // Size and opacity (Settings › Overlay / the notification), times this overlay's own size (a corner drag).
        val cfg = PerfSettings.state.value
        val a = cfg.overlayOpacity.coerceIn(20, 100) / 100f
        if (w.alpha != a) w.alpha = a
        // At 100 % the boxes are really opaque, not just their window (their own backgrounds are translucent).
        val solid = cfg.overlayOpacity >= 100
        when (v) {
            is RaOverlayView -> v.solid = solid
            is MusicOverlayView -> v.solid = solid
            is TextView -> (v.background as? GradientDrawable)?.setColor(if (solid) 0xFF000000.toInt() else 0xB0000000.toInt())
        }
        // The frame scales its contents and stays wrap-content: the window is always as big as what's drawn.
        val sc = (cfg.overlayScale.coerceIn(50, 200) / 100f * (sh?.ownSize ?: 1f)).coerceIn(0.4f, 3f)
        sh?.scale = sc
        if (lp.width != WindowManager.LayoutParams.WRAP_CONTENT || lp.height != WindowManager.LayoutParams.WRAP_CONTENT) {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT; lp.height = WindowManager.LayoutParams.WRAP_CONTENT; changed = true
        }
        if (free != null) {
            val (sw, sh2) = screenSize(w)
            w.measure(
                View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(sh2, View.MeasureSpec.AT_MOST)
            )
            // Update 179: while a corner is dragged, the opposite corner stays where it was.
            val anchor = resizeAnchors[v]
            val x = if (anchor != null) (if (anchor.right) anchor.x else anchor.x + anchor.w - w.measuredWidth).coerceIn(0, (sw - w.measuredWidth).coerceAtLeast(0))
                else (free.first * (sw - w.measuredWidth).coerceAtLeast(0)).toInt()
            val y = if (anchor != null) (if (anchor.bottom) anchor.y else anchor.y + anchor.h - w.measuredHeight).coerceIn(0, (sh2 - w.measuredHeight).coerceAtLeast(0))
                else (free.second * (sh2 - w.measuredHeight).coerceAtLeast(0)).toInt()
            if (lp.x != x || lp.y != y) { lp.x = x; lp.y = y; changed = true }
        }
        if (changed) runCatching { windowManager?.updateViewLayout(w, lp) }
    }

    /** Takes every overlay window down (the service itself keeps running). */
    private fun teardownViews() {
        scope?.cancel(); scope = null
        receiver?.let { runCatching { unregisterReceiver(it) } }; receiver = null
        view?.let { v -> runCatching { windowManager?.removeView(win(v)) } }; view = null
        musicView?.let { v -> runCatching { windowManager?.removeView(win(v)) } }; musicView = null
        raView?.let { v -> runCatching { windowManager?.removeView(win(v)) } }; raView = null
        shells.clear()
        if (acquired) { PerfSampler.release(); acquired = false }
        musicVisible.value = false
    }

    override fun onDestroy() {
        scope?.cancel(); scope = null
        receiver?.let { runCatching { unregisterReceiver(it) } }; receiver = null
        view?.let { v -> runCatching { windowManager?.removeView(win(v)) } }; view = null
        musicView?.let { v -> runCatching { windowManager?.removeView(win(v)) } }; musicView = null
        raView?.let { v -> runCatching { windowManager?.removeView(win(v)) } }; raView = null
        shells.clear()
        if (acquired) { PerfSampler.release(); acquired = false }
        PerfSampler.setPaused(false)
        alive = false
        // The shared in-game notification stays if the tuner still runs, without the overlay part.
        com.shelf.launcher.device.tune.GameNotification.overlayOn = false
        com.shelf.launcher.device.tune.GameNotification.refresh(this)
        super.onDestroy()
    }

    /**
     * Foreground with the ONE in-game notification shared with the performance tuner
     * (device/tune/GameNotification): overlays and power together, Size / Opacity / Power buttons.
     */
    private fun goForeground(): Boolean {
        com.shelf.launcher.device.tune.GameNotification.overlayOn = true
        return com.shelf.launcher.device.tune.GameNotification.foreground(this)
    }

    private fun postNotification() = com.shelf.launcher.device.tune.GameNotification.refresh(this)

    companion object {
        private const val EXTRA_ADJUST = "adjust"
        private const val EXTRA_RECONFIGURE = "reconfigure"

        /** The overlay choice changed while a game runs: a running overlay rebuilds itself (no-op otherwise). */
        fun reconfigure(context: Context) {
            if (!canDraw(context)) return
            if (alive) {
                runCatching {
                    context.startService(Intent(context, PerfOverlayService::class.java).putExtra(EXTRA_RECONFIGURE, true))
                }
                return
            }
            // Not running (the overlays were Off, or "Off" was picked a moment ago, which stops the service):
            // switching them back on mid-game starts it again, on the screen the game was on. Android may
            // refuse that from the background on some versions; then nothing changes until the next game.
            // Only while a game runs: switching overlays on in Voltcap's own menus must not put them over Voltcap.
            if (!com.shelf.launcher.data.GameMode.active) return
            val c = PerfSettings.config(context)
            val ra = c.raDuringGames && com.shelf.launcher.data.RaOverlayData.state.value != null
            if (c.showDuringGames || c.musicDuringGames || ra) {
                start(context, lastDisplay, perf = c.showDuringGames, music = c.musicDuringGames, ra = ra, newSession = false)
            }
        }

        /** The service exists (created and not yet destroyed). */
        @Volatile private var alive = false
        /** The screen the overlays last went on. */
        @Volatile private var lastDisplay: Int? = null
        private const val EXTRA_DISPLAY = "display"
        private const val EXTRA_PERF = "perf"
        private const val EXTRA_MUSIC = "music"
        private const val EXTRA_RA = "ra"

        fun canDraw(context: Context) = com.shelf.launcher.data.Permissions.granted(context, com.shelf.launcher.data.VoltPermission.OVERLAY)

        /** Starts the overlay if allowed. Call while Voltcap is still in front; a refusal is ignored. */
        fun start(context: Context, displayId: Int?, perf: Boolean = true, music: Boolean = false, ra: Boolean = false, newSession: Boolean = true) {
            if (!canDraw(context)) return
            lastDisplay = displayId
            if (newSession) OverlayClock.sessionStart = System.currentTimeMillis()
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, PerfOverlayService::class.java)
                        .putExtra(EXTRA_DISPLAY, displayId ?: android.view.Display.DEFAULT_DISPLAY)
                        .putExtra(EXTRA_PERF, perf)
                        .putExtra(EXTRA_MUSIC, music)
                        .putExtra(EXTRA_RA, ra)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PerfOverlayService::class.java)) }
        }

        private fun gravityFor(c: PerfCorner) = when (c) {
            PerfCorner.TOP_LEFT -> Gravity.TOP or Gravity.START
            PerfCorner.TOP_RIGHT -> Gravity.TOP or Gravity.END
            PerfCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
            PerfCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        }
    }
}

/** The overlay's lines for the chosen contents. Unreadable values say "n/a" - never a guess. */
internal fun overlayText(cfg: PerfConfig, s: PerfSnapshot): String {
    val lines = mutableListOf<String>()
    if (cfg.fps || cfg.frameTime) {
        val parts = mutableListOf<String>()
        if (cfg.fps) parts += s.fps?.let { "FPS ${Math.round(it)}" } ?: "FPS n/a"
        if (cfg.frameTime) parts += s.frameMs?.let { "%.1f ms".format(it) } ?: "n/a ms"
        lines += parts.joinToString(" · ")
    }
    if (cfg.cpu) {
        val f = s.cpuMaxMhz?.let { "$it MHz" } ?: "n/a"
        lines += "CPU $f" + (s.cpuPercent?.let { " · $it%" } ?: "")
    }
    if (cfg.gpu) {
        val load = s.gpuPercent?.let { "$it%" }
        val clk = s.gpuMhz?.let { "$it MHz" }
        lines += "GPU " + (listOfNotNull(load, clk).joinToString(" · ").ifEmpty { "n/a" })
    }
    if (cfg.ram) {
        lines += "RAM " + if (s.ramUsedMb != null && s.ramTotalMb != null) "%.1f/%.1f GB".format(s.ramUsedMb / 1024f, s.ramTotalMb / 1024f) else "n/a"
    }
    if (cfg.temp) {
        val soc = s.socTempC?.let { "%.0f°C".format(it) } ?: "n/a"
        lines += "Temp $soc" + (s.batteryTempC?.let { " · bat %.0f°C".format(it) } ?: "")
    }
    if (cfg.power) {
        lines += "Power " + (s.powerW?.let { "~%.1f W".format(it) } ?: "n/a")
    }
    if (cfg.batteryLeft || cfg.sessionTime || cfg.clock) {
        val parts = mutableListOf<String>()
        if (cfg.clock) parts += java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
        if (cfg.batteryLeft) parts += OverlayClock.batteryLeft(s)
        if (cfg.sessionTime) parts += "⏱ " + OverlayClock.session()
        lines += parts.joinToString(" · ")
    }
    if (cfg.warnings) {
        // Only when it matters - and never twice (the temperature line already shows the number).
        val hot = s.socTempC?.takeIf { it >= 80f }
        val low = s.batteryPercent?.takeIf { it <= 15 && s.charging != true }
        if (hot != null) lines += "⚠ Running hot" + (if (cfg.temp) "" else " (%.0f°C)".format(hot))
        if (low != null) lines += "⚠ Battery low" + (if (cfg.batteryLeft) "" else " ($low%)")
    }
    return lines.joinToString("\n").ifEmpty { "Performance" }
}


/** Session timer and a smoothed battery-time estimate for the overlay. */
internal object OverlayClock {
    @Volatile var sessionStart: Long = System.currentTimeMillis()
    private var avgW: Float? = null

    fun session(): String {
        val m = ((System.currentTimeMillis() - sessionStart) / 60_000L).coerceAtLeast(0)
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    fun batteryLeft(s: PerfSnapshot): String {
        val pct = s.batteryPercent?.let { "$it%" } ?: "Bat n/a"
        if (s.charging == true) return "$pct charging"
        val w = s.powerW?.takeIf { it > 0.3f } ?: return pct
        val a = avgW?.let { it * 0.9f + w * 0.1f } ?: w
        avgW = a
        val wh = s.batteryWh ?: return pct
        val min = (wh / a * 60f).toInt()
        return "$pct · ~" + if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
    }
}


/**
 * The RetroAchievements overlay's text: progress on the first line (gold), the last unlock, the
 * missables still to get (amber warning, by name, a few at most) and the next one worth going for.
 * Nothing is repeated: the next achievement isn't listed again among the missables.
 */
internal fun raOverlayText(st: com.shelf.launcher.data.RaOverlayData.State, cfg: PerfConfig): CharSequence {
    val b = android.text.SpannableStringBuilder()
    fun line(text: String, color: Int, bold: Boolean = false, small: Boolean = false) {
        if (b.isNotEmpty()) b.append('\n')
        val start = b.length
        b.append(text)
        b.setSpan(android.text.style.ForegroundColorSpan(color), start, b.length, 0)
        if (bold) b.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, b.length, 0)
        if (small) b.setSpan(android.text.style.RelativeSizeSpan(0.88f), start, b.length, 0)
    }
    val pct = if (st.total > 0) st.unlocked * 100 / st.total else 0
    line("🏆 ${st.unlocked}/${st.total} · $pct %" + (if (cfg.raPoints) " · ${st.points}/${st.totalPoints} pts" else ""), 0xFFFFC94D.toInt(), bold = true)
    if (cfg.raLastUnlock) st.lastUnlock?.let { line("✓ $it", 0xFF5BD68A.toInt(), small = true) }
    if (cfg.raMissables && st.missablesLeft.isNotEmpty()) {
        val others = st.missablesLeft.filter { it != st.nextTitle }
        val n = st.missablesLeft.size
        val names = others.take(2).joinToString(", ") + if (others.size > 2) " +${others.size - 2}" else ""
        line("⚠ $n missable${if (n == 1) "" else "s"} still to get" + if (names.isNotEmpty()) ": $names" else "", 0xFFFFB547.toInt(), small = true)
    }
    if (cfg.raNext && st.nextTitle != null) {
        line("Next: ${st.nextTitle}", if (st.nextIsMissable) 0xFFFFB547.toInt() else Color.WHITE, bold = true)
        st.nextDescription?.takeIf { it.isNotBlank() }?.let { line(it, 0xCCFFFFFF.toInt(), small = true) }
        st.nextWhy?.let { line(it, 0x99FFFFFF.toInt(), small = true) }
    }
    if (st.total > 0 && st.unlocked == st.total) line("Mastered - every achievement unlocked", 0xFFFFC94D.toInt())
    return b
}


/** Free spots of overlays dragged away from the corners (Features.overlayPositions). */
internal object OverlayPositions {
    private fun parse(): Map<String, Pair<Float, Float>> = com.shelf.launcher.data.Features.overlayPositions.value.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val xy = part.substring(i + 1).split(',')
        val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        part.substring(0, i) to (x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f))
    }.toMap()

    private fun save(m: Map<String, Pair<Float, Float>>) {
        com.shelf.launcher.data.Features.overlayPositions.value =
            m.entries.joinToString(";") { "${it.key}=${"%.4f".format(java.util.Locale.US, it.value.first)},${"%.4f".format(java.util.Locale.US, it.value.second)}" }
    }

    fun get(key: String): Pair<Float, Float>? = parse()[key]
    fun set(key: String, fx: Float, fy: Float) = save(parse() + (key to (fx.coerceIn(0f, 1f) to fy.coerceIn(0f, 1f))))
    fun clear(key: String) { val m = parse(); if (key in m) save(m - key) }
    /** Every overlay back in its corner, at the plain Overlay size and unfolded. */
    fun clearAll() {
        com.shelf.launcher.data.Features.overlayPositions.value = ""
        com.shelf.launcher.data.Features.overlaySizes.value = ""
        com.shelf.launcher.data.Features.overlayFolded.value = emptySet()
    }

    /** An overlay's own size from a corner drag (1 = none). */
    fun size(key: String): Float = com.shelf.launcher.data.Features.overlaySizes.value.split(';')
        .firstNotNullOfOrNull { part -> part.split('=').takeIf { it.size == 2 && it[0] == key }?.get(1)?.toFloatOrNull() }
        ?.coerceIn(0.3f, 3f) ?: 1f

    fun setSize(key: String, size: Float) {
        val rest = com.shelf.launcher.data.Features.overlaySizes.value.split(';').filter { it.isNotBlank() && !it.startsWith("$key=") }
        val mine = if (kotlin.math.abs(size - 1f) < 0.02f) emptyList() else listOf("$key=${"%.2f".format(java.util.Locale.US, size.coerceIn(0.3f, 3f))}")
        com.shelf.launcher.data.Features.overlaySizes.value = (rest + mine).joinToString(";")
    }
    fun any(): Boolean = com.shelf.launcher.data.Features.overlayPositions.value.isNotBlank()
}
