package com.shelf.launcher.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.shelf.launcher.data.AppearanceStore
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.ScreenPlacement
import com.shelf.launcher.data.ShelfNotificationListener
import com.shelf.launcher.data.ThemeStore
import com.shelf.launcher.ui.theme.ShelfLauncherTheme
import com.shelf.launcher.ui.theme.ShelfTheme
import java.lang.ref.WeakReference

/**
 * What Voltcap puts on the screen the game isn't on while it runs: the game's
 * info with a play timer, or plain black.
 *
 * It's its own activity (not the home screen's second-screen view) because
 * the home screen stops drawing once a game covers it. It closes itself when
 * Voltcap comes back ([closeAll], called from the home screen's resume), and
 * B / Back / the button on it take you back to Voltcap.
 */
class CompanionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Idempotent: also records crashes of this screen (see CrashGuard).
        com.shelf.launcher.CrashGuard.install(this)
        super.onCreate(savedInstanceState)
        open.add(WeakReference(this))
        // Touch / gesture back does the same as B.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = backToVoltcap()
        })
        hideSystemBars()
        val extras = intent.extras ?: Bundle()
        val black = extras.getString(EXTRA_MODE) == MODE_BLACK
        val performance = extras.getString(EXTRA_MODE) == MODE_PERFORMANCE
        val info = CompanionInfo(
            title = extras.getString(EXTRA_TITLE).orEmpty(),
            platform = extras.getString(EXTRA_PLATFORM).orEmpty(),
            coverUrl = extras.getString(EXTRA_COVER),
            heroUrl = extras.getString(EXTRA_HERO),
            playtimeMs = extras.getLong(EXTRA_PLAYTIME, 0L),
            raEarned = extras.getInt(EXTRA_RA_EARNED, 0),
            raPossible = extras.getInt(EXTRA_RA_POSSIBLE, 0),
            startedAt = extras.getLong(EXTRA_STARTED, System.currentTimeMillis()),
            gameScreen = extras.getString(EXTRA_GAME_SCREEN).orEmpty(),
            gameKey = extras.getString(EXTRA_GAME_KEY).orEmpty()
        )
        val theme = runCatching { ThemeStore(this).load() }.getOrNull()
        val appearance = runCatching { AppearanceStore(this).load() }.getOrNull()
        appearance?.let { AlbumArtBlur.sync(it.secondMusicBlurArt, it.secondMusicBlur) }
        setContent {
            if (black) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            } else if (theme != null && appearance != null) {
                ShelfLauncherTheme(appTheme = theme, appearance = appearance, dynamicArtUrl = info.coverUrl) {
                    val vm = CompanionBridge.vm?.get()
                    if (performance) CompanionPerfScreen(info, onBack = ::backToVoltcap)
                    else if (vm != null && info.gameKey.isNotEmpty()) CompanionHub(vm, info, onBack = ::backToVoltcap)
                    else CompanionScreen(info, onBack = ::backToVoltcap)
                }
            } else {
                ShelfLauncherTheme {
                    if (performance) CompanionPerfScreen(info, onBack = ::backToVoltcap)
                    else CompanionScreen(info, onBack = ::backToVoltcap)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            backToVoltcap()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() { super.onResume(); CompanionBridge.open = true }
    override fun onPause() { CompanionBridge.open = false; super.onPause() }

    override fun onDestroy() {
        open.removeAll { it.get() == null || it.get() === this }
        super.onDestroy()
    }

    /** Closes this and brings Voltcap's home screen up on the main screen. */
    private fun backToVoltcap() {
        val home = Intent(this, com.shelf.launcher.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(home, ScreenPlacement.optionsFor(android.view.Display.DEFAULT_DISPLAY)) }
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        const val MODE_INFO = "info"
        const val MODE_BLACK = "black"
        /** Live performance readouts (the game's second-screen page is Performance). */
        const val MODE_PERFORMANCE = "performance"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PLATFORM = "platform"
        private const val EXTRA_COVER = "cover"
        private const val EXTRA_HERO = "hero"
        private const val EXTRA_PLAYTIME = "playtime"
        private const val EXTRA_RA_EARNED = "ra_earned"
        private const val EXTRA_RA_POSSIBLE = "ra_possible"
        private const val EXTRA_STARTED = "started"
        private const val EXTRA_GAME_SCREEN = "game_screen"
        private const val EXTRA_GAME_KEY = "game_key"

        private val open = mutableListOf<WeakReference<CompanionActivity>>()

        /** Called when Voltcap's home screen comes back: the game is over. */
        fun closeAll() {
            open.toList().forEach { ref -> ref.get()?.let { runCatching { it.finish() } } }
            open.clear()
        }

        /** Starts the companion on [displayId]. False if Android refused. */
        fun start(context: Context, displayId: Int, mode: String, info: CompanionInfo?): Boolean {
            closeAll()
            val intent = Intent(context, CompanionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(EXTRA_MODE, mode)
                if (info != null) {
                    putExtra(EXTRA_TITLE, info.title)
                    putExtra(EXTRA_PLATFORM, info.platform)
                    putExtra(EXTRA_COVER, info.coverUrl)
                    putExtra(EXTRA_HERO, info.heroUrl)
                    putExtra(EXTRA_PLAYTIME, info.playtimeMs)
                    putExtra(EXTRA_RA_EARNED, info.raEarned)
                    putExtra(EXTRA_RA_POSSIBLE, info.raPossible)
                    putExtra(EXTRA_STARTED, info.startedAt)
                    putExtra(EXTRA_GAME_SCREEN, info.gameScreen)
                    putExtra(EXTRA_GAME_KEY, info.gameKey)
                }
            }
            return runCatching {
                context.startActivity(intent, ScreenPlacement.optionsFor(displayId))
                true
            }.getOrDefault(false)
        }
    }
}

/** Everything the companion shows, handed over when the game starts. */
data class CompanionInfo(
    val title: String,
    val platform: String,
    val coverUrl: String?,
    val heroUrl: String?,
    val playtimeMs: Long,
    val raEarned: Int,
    val raPossible: Int,
    val startedAt: Long,
    /** "main screen", "TV"… — where the game is, for the hint line. */
    val gameScreen: String,
    /** The game's stableKey, to show its pages live from the home screen's data. */
    val gameKey: String = ""
)

@Composable
private fun CompanionScreen(info: CompanionInfo, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var music by remember { mutableStateOf<Pair<NowPlaying, android.media.session.MediaController>?>(null) }
    // Session clock: 1 s, only while this activity is resumed and the screen is on.
    LaunchedWhileActive {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    // Track changes arrive as MediaController callbacks instead of a 1 s poll.
    NowPlayingUpdates(enabled = true) {
        music = runCatching { ShelfNotificationListener.nowPlaying(context) }.getOrNull()
    }
    val session = (now - info.startedAt).coerceAtLeast(0L)

    Box(Modifier.fillMaxSize().background(ShelfTheme.background)) {
        if (info.heroUrl != null) {
            // Blurred once (update 121), not again on every tick of the session clock over it.
            BakedBlurImage(info.heroUrl, 32.dp, Modifier.fillMaxSize(), alpha = 0.4f)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, ShelfTheme.background.copy(alpha = 0.9f)))
            )
        )
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // The game's card takes the room left above the player and the Back row (and scrolls on a short
            // screen), so the player and Back never get pushed off the bottom.
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(96.dp)
                        .height(134.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    if (info.coverUrl != null) {
                        AsyncImage(
                            model = info.coverUrl, contentDescription = info.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        gameDisplayName(info.title), color = ShelfTheme.colors.textPrimary, fontSize = 24.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(24.sp), maxFontSize = 24.sp),
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(info.platform, color = ShelfTheme.secondary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This session  " + clock(session),
                        color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    val total = info.playtimeMs + session
                    if (total >= 60_000L) {
                        Text("Played ${PlaytimeStore.format(total)} in total", color = ShelfTheme.textMuted, fontSize = 13.sp)
                    }
                    if (info.raPossible > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            com.shelf.launcher.data.achievementProgress(info.raEarned, info.raPossible) + " achievements",
                            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth((info.raEarned.toFloat() / info.raPossible).coerceIn(0f, 1f))
                                    .height(5.dp).background(ShelfTheme.accent)
                            )
                        }
                    }
                }
            }
            }
            Spacer(Modifier.height(12.dp))
            music?.let { (playing, controller) ->
                MusicPlayer(
                    playing,
                    onPlayPause = {
                        val on = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                        if (on) controller.transportControls.pause() else controller.transportControls.play()
                    },
                    onNext = { controller.transportControls.skipToNext() },
                    onPrevious = { controller.transportControls.skipToPrevious() },
                    onSeek = { controller.transportControls.seekTo(it.coerceAtLeast(0L)) }
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.gameScreen.isNotBlank()) {
                    Text(
                        "Playing on the ${info.gameScreen}",
                        color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Chip("Back to Voltcap", accent = false, onClick = onBack)
            }
        }
    }
}

/** The same live readouts as the second-screen Performance page; the sampler runs only while this activity is resumed. */
@Composable
private fun CompanionPerfScreen(info: CompanionInfo, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ShelfTheme.background)) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Text(
                gameDisplayName(info.title), color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp),
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            PerfPage()
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Chip("Back to Voltcap", accent = false, onClick = onBack)
            }
        }
    }
}

/** 1:05:09 / 12:03. */
private fun clock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
