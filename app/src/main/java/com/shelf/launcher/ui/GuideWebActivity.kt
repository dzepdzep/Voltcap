package com.shelf.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Guides (RetroAchievements guides, GameFAQs pages, community links) read inside Voltcap instead of
 * the browser. Controller-friendly: ▲ ▼ scroll, L1 / R1 (or ◀ ▶) page up / down, B goes back a page
 * and closes on the first one. Y saves the page for reading offline (OfflineGuides: a web archive in
 * Voltcap's own files); saved pages open here again without a connection. Cookies stay inside the WebView.
 */
class GuideWebActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_URL)
        gameKey = intent?.getStringExtra(EXTRA_GAME)
        gameTitle = intent?.getStringExtra(EXTRA_TITLE)
        // A saved (offline) guide: only files from Voltcap's own saved-guides folder.
        val savedFile = url?.takeIf { it.startsWith("file://") }?.removePrefix("file://")
        val local = savedFile != null && com.shelf.launcher.data.guide.OfflineGuides.isSavedFile(this, savedFile)
        if (url.isNullOrBlank() || !(local || url.startsWith("https://") || url.startsWith("http://"))) { finish(); return }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#111111")) }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false; max = 100
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 115
            // Reading a saved archive needs file access; web pages never get it.
            settings.allowFileAccess = local
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = object : WebViewClient() {
                // Links stay inside; only web pages (no intent:// or app links) are followed.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme ?: return true
                    return scheme != "https" && scheme != "http"
                }
                override fun onPageFinished(view: WebView, url: String?) { progress.visibility = android.view.View.GONE }
            }
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.visibility = if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
                    progress.progress = newProgress
                }
            }
        }
        // Files (a scanned manual's PDF, say) go to the Downloads folder through Android's downloader.
        web.setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
            runCatching {
                val name = android.webkit.URLUtil.guessFileName(dlUrl, contentDisposition, mimeType)
                val req = android.app.DownloadManager.Request(android.net.Uri.parse(dlUrl))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
                android.widget.Toast.makeText(this, "Downloading $name to Downloads", android.widget.Toast.LENGTH_LONG).show()
            }.onFailure {
                android.widget.Toast.makeText(this, "Couldn't download this file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8))
        val hint = TextView(this).apply {
            text = if (local) "Saved guide (offline) · ▲▼ scroll · L1/R1 page · B back"
                else if (gameKey != null) "▲▼ scroll · L1/R1 page · X add to this game · Y save for offline · B back"
                else "▲▼ scroll · L1/R1 page · B back"
            setTextColor(Color.argb(170, 255, 255, 255)); textSize = 11f
            setBackgroundColor(Color.argb(150, 0, 0, 0)); setPadding(16, 6, 16, 6)
        }
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.END))
        setContentView(root)
        web.loadUrl(url)
        web.requestFocus()
    }

    // Update 130: Hide navigation bar applies to this window too (it used to bring the bar back while open).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) runCatching { SystemBars.apply(window) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::web.isInitialized) return super.dispatchKeyEvent(event)
        val step = (web.height * 0.12f).toInt()
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> { web.scrollBy(0, step); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { web.scrollBy(0, -step); return true }
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> { web.pageDown(false); return true }
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> { web.pageUp(false); return true }
                KeyEvent.KEYCODE_BUTTON_R2 -> { web.pageDown(true); return true }
                KeyEvent.KEYCODE_BUTTON_L2 -> { web.pageUp(true); return true }
                // Update 144: claim the press of B / Back / Y / X too. Unclaimed, Android sends a stand-in Back
                // that ran a second time (one B went back two pages; Y saved and then closed the reader).
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X -> return true
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            // Update 144: a cancelled release (the stand-in key being called off) never goes back or closes.
            if (event.isCanceled) return super.dispatchKeyEvent(event)
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                    if (web.canGoBack()) web.goBack() else finish()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> { saveOffline(); return true }
                KeyEvent.KEYCODE_BUTTON_X -> { addToGame(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private var gameKey: String? = null
    private var gameTitle: String? = null

    /** Y: keeps the page as it's shown now, for reading without a connection (per game). */
    private fun saveOffline() {
        val key = gameKey ?: return
        val pageUrl = web.url ?: return
        if (pageUrl.startsWith("file://")) return
        val title = web.title?.takeIf { it.isNotBlank() } ?: gameTitle ?: "Guide"
        val file = com.shelf.launcher.data.guide.OfflineGuides.newFile(this, key, title)
        web.saveWebArchive(file.path, false) { path ->
            if (path != null) {
                com.shelf.launcher.data.guide.OfflineGuides.add(this, key, title, pageUrl, file)
                android.widget.Toast.makeText(this, "Saved for offline: $title", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "Couldn't save this page", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** X: adds the page to the game's guides (a link), e.g. after searching for one. */
    private fun addToGame() {
        val key = gameKey ?: return
        val pageUrl = web.url ?: return
        if (!pageUrl.startsWith("http")) return
        val title = web.title?.takeIf { it.isNotBlank() } ?: gameTitle ?: "Guide"
        val added = com.shelf.launcher.data.guide.GameGuideLinks.add(this, key, title, pageUrl)
        android.widget.Toast.makeText(
            this, if (added) "Added to ${gameTitle ?: "the game"}'s guides: $title" else "Already in this game's guides", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // Videos and sounds on a guide page stop while Voltcap's reader is in the background.
    override fun onPause() {
        if (::web.isInitialized) runCatching { web.onPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) runCatching { web.onResume() }
    }

    override fun onDestroy() {
        if (::web.isInitialized) runCatching { web.destroy() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_GAME = "game"
        private const val EXTRA_TITLE = "title"
        /** [gameKey] / [gameTitle]: the game it's for, so Y can save the page for that game. */
        fun open(context: Context, url: String, gameKey: String? = null, gameTitle: String? = null) {
            runCatching {
                context.startActivity(
                    Intent(context, GuideWebActivity::class.java).putExtra(EXTRA_URL, url)
                        .putExtra(EXTRA_GAME, gameKey).putExtra(EXTRA_TITLE, gameTitle)
                        .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}
