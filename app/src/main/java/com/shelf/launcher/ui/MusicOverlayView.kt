package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shelf.launcher.data.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The music chip over a game. Collapsed: album art, title · artist and the current lyric line —
 * small, in a corner. With "Tap to open controls" on, a tap expands it into a panel with
 * previous / play-pause / next and the Spotify library (playlists and albums, tap to play); a tap
 * outside, the ✕ or a few idle seconds collapse it again.
 *
 * Touch only: the window never takes focus, so the controller always stays with the game. While
 * collapsed only the chip itself is touchable (everything around it goes to the game); with
 * interaction off it isn't touchable at all. The album art behind it uses the one shared blur
 * setting ([AlbumArtBlur]).
 */
internal class MusicOverlayView(
    context: Context,
    private val scope: CoroutineScope,
    /** The panel opened / closed: the service updates the window's touch flags. */
    private val onExpandedChange: (Boolean) -> Unit
) : FrameLayout(context) {
    private val d = context.resources.displayMetrics.density
    private fun px(dp: Float) = (dp * d).toInt()

    var interactive: Boolean = true
    /** Overlay opacity at 100 %: an opaque base instead of the see-through box. Set by the service. */
    var solid: Boolean = false
        set(v) { if (field != v) { field = v; (background as? GradientDrawable)?.setColor(if (v) 0xFF101014.toInt() else 0xC0101014.toInt()) } }
    var expanded: Boolean = false
        private set

    // The blurred cover behind the chip, like the home screen's players: clearly visible (it used to sit
    // at 60 % under a near-black scrim, so the blur setting seemed to do nothing over games).
    private val backdrop = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0.95f
        visibility = View.GONE
    }
    /** Only enough shade for the text to read; darker when there's no cover behind it. */
    private val scrimWithArt = intArrayOf(0x40101014, 0x8C101014.toInt())
    private val scrimPlain = intArrayOf(0x88101014.toInt(), 0xE6101014.toInt())
    private val scrim = View(context).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, scrimPlain)
    }
    private val art = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 6 * d)
            }
        }
    }
    private val title = text(11f, bold = true, alpha = 1f)
    private val artist = text(9f, bold = false, alpha = 0.7f)
    private val lyric = text(9.5f, bold = false, alpha = 0.95f).apply { maxLines = 2 }
    private val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(title); addView(artist); addView(lyric)
    }
    /** Width of the text column in dp (collapsed / open). */
    private var textColDp = 150f

    // ---- song buttons right on the chip (Settings › Overlay: "Song buttons on the chip") ----
    private val miniPlay = miniButton("⏯") { mediaAction { c, playing -> if (playing) c.transportControls.pause() else c.transportControls.play() } }
    /** ♪ on the chip: lyrics on / off without leaving the game (the "Lyrics line" setting). */
    private val miniLyrics = miniButton("♪") {
        com.shelf.launcher.data.perf.PerfSettings.update(context) { it.copy(musicLyrics = !it.musicLyrics) }
    }
    private val miniControls = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(miniButton("⏮") { mediaAction { c, _ -> c.transportControls.skipToPrevious() } })
        addView(miniPlay)
        addView(miniButton("⏭") { mediaAction { c, _ -> c.transportControls.skipToNext() } })
        addView(miniLyrics)
        visibility = View.GONE
    }
    /** Shows / hides the song buttons on the collapsed chip. */
    var chipButtons: Boolean = true
        set(value) { field = value; updateMiniControls() }
    private val close = text(14f, bold = true, alpha = 0.85f).apply {
        text = "✕"
        setPadding(px(8f), 0, px(2f), 0)
        visibility = View.GONE
        setOnClickListener { collapse() }
    }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    // ---- expanded part ----
    private val playPause = controlButton("⏯") { mediaAction { c, playing -> if (playing) c.transportControls.pause() else c.transportControls.play() } }
    /** Update 124: shuffle while Spotify plays (MusicShuffle.kt); the result shows on the lyric line for a moment. */
    private val shuffle = controlButton("🔀") {
        touched()
        scope.launch {
            val (state, error) = MusicShuffle.toggle(context, null)
            lyric.text = error ?: when (state) { true -> "Shuffle on"; false -> "Shuffle off"; null -> "Shuffle" }
            lyric.visibility = View.VISIBLE
        }
    }.apply { visibility = View.GONE }
    private val controls = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(shuffle)
        addView(controlButton("⏮") { mediaAction { c, _ -> c.transportControls.skipToPrevious() } })
        addView(playPause)
        addView(controlButton("⏭") { mediaAction { c, _ -> c.transportControls.skipToNext() } })
    }
    private val tabPlaylists = tab("Playlists")
    private val tabAlbums = tab("Albums")
    private val tabs = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(tabPlaylists); addView(tabAlbums)
    }
    private val libraryRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val libraryNote = text(10f, bold = false, alpha = 0.75f).apply { maxLines = 2 }
    private val libraryScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(libraryRow)
    }
    private val expandedPart = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
        addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
        addView(libraryNote)
        addView(libraryScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)).apply { topMargin = px(4f) })
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = px(6f)
        setPadding(pad, pad, pad + pad / 2, pad)
        addView(header)
        addView(expandedPart)
    }

    private var lastArt: android.graphics.Bitmap? = null
    private var lastBlurKey: String? = null
    private var showingAlbums = false
    private var library: Pair<List<LibraryItem>, List<LibraryItem>>? = null
    private var libraryLoading = false
    private val handler = Handler(Looper.getMainLooper())
    private val autoCollapse = Runnable { collapse() }

    private class LibraryItem(val name: String, val uri: String, val image: String?)

    init {
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 10 * d)
            }
        }
        background = GradientDrawable().apply { setColor(0xC0101014.toInt()); cornerRadius = 10 * d }
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val size = px(30f)
        header.addView(art, LinearLayout.LayoutParams(size, size))
        header.addView(textCol, LinearLayout.LayoutParams(px(150f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(7f) })
        header.addView(miniControls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(4f) })
        header.addView(close)
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(content, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        tabPlaylists.setOnClickListener { showingAlbums = false; renderLibrary(); touched() }
        tabAlbums.setOnClickListener { showingAlbums = true; renderLibrary(); touched() }
    }

    /**
     * The chip is exactly as big as its contents. The blurred cover and the scrim used to be measured
     * against the whole screen (they're "match parent" inside a wrap-content window), so the chip
     * grew to the cover's own size — half the screen, full height. Now only the contents decide the
     * size and the backdrop is fitted to it.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildWithMargins(content, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val w = content.measuredWidth
        val h = content.measuredHeight
        val ew = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val eh = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        backdrop.measure(ew, eh)
        scrim.measure(ew, eh)
        setMeasuredDimension(w, h)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Collapsed: a tap on the chip opens the panel (its children never get it) — except on the song
        // buttons, which work right away.
        if (!interactive || expanded || ev.actionMasked != MotionEvent.ACTION_DOWN) return false
        return !(miniControls.visibility == View.VISIBLE && hits(miniControls, ev))
    }

    private fun hits(v: View, ev: MotionEvent): Boolean {
        val r = android.graphics.Rect()
        v.getHitRect(r)
        // The header's coordinates inside the content padding.
        r.offset(content.left + header.left, content.top + header.top)
        return r.contains(ev.x.toInt(), ev.y.toInt())
    }

    private fun updateMiniControls() {
        // Song buttons on the chip while collapsed (the open panel has the big ones); they need touches.
        miniControls.visibility = if (chipButtons && interactive && !expanded) View.VISIBLE else View.GONE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> { if (expanded) collapse(); return false }
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> { if (!expanded) expand() else touched(); return true }
        }
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (expanded && ev.actionMasked == MotionEvent.ACTION_DOWN) touched()
        return super.dispatchTouchEvent(ev)
    }

    fun expand() {
        if (expanded || !interactive) return
        expanded = true
        close.visibility = View.VISIBLE
        expandedPart.visibility = View.VISIBLE
        updateMiniControls()
        textColDp = 210f
        textCol.layoutParams = (textCol.layoutParams as LinearLayout.LayoutParams).apply { width = px(210f) }
        onExpandedChange(true)
        loadLibrary()
        touched()
    }

    fun collapse() {
        handler.removeCallbacks(autoCollapse)
        if (!expanded) return
        expanded = false
        close.visibility = View.GONE
        expandedPart.visibility = View.GONE
        updateMiniControls()
        textColDp = 150f
        textCol.layoutParams = (textCol.layoutParams as LinearLayout.LayoutParams).apply { width = px(150f) }
        onExpandedChange(false)
    }

    /** Any touch in the open panel restarts its idle timer. */
    private fun touched() {
        handler.removeCallbacks(autoCollapse)
        handler.postDelayed(autoCollapse, 8_000L)
    }

    fun bind(np: NowPlaying, line: String?) {
        if (np.art !== lastArt) {
            lastArt = np.art
            if (np.art != null) { art.setImageBitmap(np.art); art.visibility = View.VISIBLE } else art.visibility = View.GONE
            lastBlurKey = null
        }
        // The shared album-art blur (Settings › Music & Audio): off = no art behind the chip.
        val blurKey = "${System.identityHashCode(np.art)}|${AlbumArtBlur.enabled}|${AlbumArtBlur.percent}"
        if (blurKey != lastBlurKey) {
            lastBlurKey = blurKey
            val a = np.art
            (scrim.background as? GradientDrawable)?.colors = if (a == null || !AlbumArtBlur.enabled) scrimPlain else scrimWithArt
            if (a == null || !AlbumArtBlur.enabled) {
                backdrop.visibility = View.GONE
            } else if (Build.VERSION.SDK_INT >= 31) {
                backdrop.setImageBitmap(a)
                val r = AlbumArtBlur.radius(28f).value * d
                backdrop.setRenderEffect(
                    if (r > 0.5f) android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP) else null
                )
                backdrop.visibility = View.VISIBLE
            } else {
                backdrop.setImageBitmap(AlbumArtBlur.softened(a) ?: a)
                backdrop.visibility = View.VISIBLE
            }
        }
        title.text = (if (np.isPlaying) "" else "❚❚ ") + (np.title ?: "")
        artist.text = np.artist ?: ""
        artist.visibility = if (np.artist.isNullOrBlank()) View.GONE else View.VISIBLE
        if (lyric.text?.toString() != (line ?: "")) fitLyric(line ?: "")
        lyric.visibility = if (line.isNullOrBlank()) View.GONE else View.VISIBLE
        playPause.text = if (np.isPlaying) "⏸" else "▶"
        shuffle.visibility = if (MusicShuffle.isSpotify(np.packageName)) View.VISIBLE else View.GONE
        miniPlay.text = if (np.isPlaying) "⏸" else "▶"
        miniLyrics.alpha = if (com.shelf.launcher.data.perf.PerfSettings.state.value.musicLyrics) 1f else 0.45f
        updateMiniControls()
    }

    /**
     * A long lyric wraps onto a second line and, if it still doesn't fit, gets a little smaller (down to
     * 7.5 sp) and a third line — instead of ending in "…" after a few words.
     */
    private fun fitLyric(line: String) {
        val avail = px(textColDp) * 2f * 0.96f
        val paint = android.text.TextPaint(lyric.paint)
        val scaled = resources.displayMetrics.scaledDensity
        var sp = 9.5f
        while (sp > 7.5f) {
            paint.textSize = sp * scaled
            if (paint.measureText(line) <= avail) break
            sp -= 0.5f
        }
        paint.textSize = sp * scaled
        lyric.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        lyric.maxLines = if (paint.measureText(line) <= avail) 2 else 3
        lyric.text = line
    }

    // ---- media controls (any player, through its media session) ----

    private fun mediaAction(block: (android.media.session.MediaController, Boolean) -> Unit) {
        touched()
        runCatching {
            val pair = com.shelf.launcher.data.ShelfNotificationListener.nowPlaying(context) ?: return
            val c = pair.second
            val playing = c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            block(c, playing)
        }
    }

    // ---- Spotify library ----

    private fun loadLibrary() {
        if (library != null || libraryLoading) { renderLibrary(); return }
        val repo = com.shelf.launcher.spotify.SpotifyRepository(context.applicationContext)
        if (repo.connection.value != com.shelf.launcher.spotify.SpotifyConnection.CONNECTED) {
            libraryNote.text = "Connect Spotify in Voltcap (Settings › Accounts) to pick playlists here."
            libraryNote.visibility = View.VISIBLE
            tabs.visibility = View.GONE
            libraryScroll.visibility = View.GONE
            return
        }
        libraryLoading = true
        libraryNote.text = "Loading your library…"
        libraryNote.visibility = View.VISIBLE
        scope.launch {
            val pr = runCatching { repo.myPlaylistsWithLiked() }.getOrNull() as? com.shelf.launcher.spotify.SpotifyResult.Ok
            val ar = runCatching { repo.savedAlbums() }.getOrNull() as? com.shelf.launcher.spotify.SpotifyResult.Ok
            val playlists = pr?.value.orEmpty()
                .mapNotNull { p -> p.uri?.let { LibraryItem(p.name ?: "Playlist", it, p.images?.lastOrNull()?.url ?: p.images?.firstOrNull()?.url) } }
            val albums = ar?.value?.clean().orEmpty()
                .mapNotNull { s -> s.album?.let { a -> a.uri?.let { LibraryItem(a.name ?: "Album", it, a.images?.lastOrNull()?.url ?: a.images?.firstOrNull()?.url) } } }
            libraryLoading = false
            if (pr == null && ar == null) {
                // Nothing came back (offline, Spotify busy): say so, and try again the next time it opens.
                libraryNote.text = "Couldn't reach Spotify just now. Close and open this again to retry."
                libraryNote.visibility = View.VISIBLE
                tabs.visibility = View.GONE
                libraryScroll.visibility = View.GONE
                return@launch
            }
            library = playlists to albums
            renderLibrary()
        }
    }

    private fun renderLibrary() {
        val lib = library ?: return
        tabs.visibility = View.VISIBLE
        styleTab(tabPlaylists, !showingAlbums)
        styleTab(tabAlbums, showingAlbums)
        val items = if (showingAlbums) lib.second else lib.first
        libraryRow.removeAllViews()
        if (items.isEmpty()) {
            libraryNote.text = if (showingAlbums) "No saved albums." else "No playlists."
            libraryNote.visibility = View.VISIBLE
            libraryScroll.visibility = View.GONE
            return
        }
        libraryNote.visibility = View.GONE
        libraryScroll.visibility = View.VISIBLE
        items.distinctBy { it.uri }.take(40).forEach { item -> libraryRow.addView(libraryTile(item)) }
    }

    private fun libraryTile(item: LibraryItem): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(3f), 0, px(3f), 0)
            setOnClickListener {
                touched()
                com.shelf.launcher.ui.BackgroundMusicGate.otherMusicStarting()
                val repo = com.shelf.launcher.spotify.SpotifyRepository(context.applicationContext)
                scope.launch {
                    runCatching {
                        // Liked Songs is the user's collection (or our own id when the account id was unknown).
                        if (item.uri.endsWith(":collection") || item.uri == com.shelf.launcher.spotify.LIKED_ID) repo.playLiked(item.uri)
                        else repo.play(contextUri = item.uri)
                    }
                }
                lyric.text = "Playing ${item.name}…"
                lyric.visibility = View.VISIBLE
            }
        }
        val img = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 6 * d)
                }
            }
            setBackgroundColor(0x33FFFFFF)
        }
        item.image?.let { url ->
            runCatching {
                coil.Coil.imageLoader(context).enqueue(
                    coil.request.ImageRequest.Builder(context).data(url).size(px(44f)).target(img).build()
                )
            }
        }
        tile.addView(img, LinearLayout.LayoutParams(px(44f), px(44f)))
        tile.addView(text(8f, bold = false, alpha = 0.9f).apply {
            text = item.name
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(px(56f), ViewGroup.LayoutParams.WRAP_CONTENT))
        return tile
    }

    private fun styleTab(t: TextView, selected: Boolean) {
        t.alpha = if (selected) 1f else 0.6f
        t.background = GradientDrawable().apply {
            cornerRadius = 50 * d
            setColor(if (selected) 0x40FFFFFF else 0x14FFFFFF)
        }
    }

    private fun tab(label: String) = text(10f, bold = true, alpha = 1f).apply {
        text = label
        setPadding(px(10f), px(3f), px(10f), px(3f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(6f) }
    }

    private fun controlButton(label: String, onClick: () -> Unit) = text(18f, bold = true, alpha = 1f).apply {
        text = label
        gravity = Gravity.CENTER
        setPadding(px(14f), px(4f), px(14f), px(4f))
        background = GradientDrawable().apply { cornerRadius = 50 * d; setColor(0x22FFFFFF) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = px(4f); marginEnd = px(4f)
        }
        setOnClickListener { onClick() }
    }

    /** A small round song button for the collapsed chip. */
    private fun miniButton(label: String, onClick: () -> Unit) = text(12f, bold = true, alpha = 1f).apply {
        text = label
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x26FFFFFF) }
        layoutParams = LinearLayout.LayoutParams(px(24f), px(24f)).apply { marginStart = px(2f) }
        setOnClickListener { onClick() }
    }

    private fun text(sp: Float, bold: Boolean, alpha: Float) = TextView(context).apply {
        setTextColor(Color.WHITE)
        this.alpha = alpha
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        // D79: a long title / artist scrolls (marquee) instead of ending in "…"; the full text stays reachable.
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isSelected = true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(autoCollapse)
        super.onDetachedFromWindow()
    }
}
