package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.shownIn
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/**
 * Second screen › Details, like a game's page on a real shelf: over the game's blurred art, the real case on the
 * left — the box scan in 3D with the system's spine (your template pack) — and on the right the console, the
 * name, year · developer · genres and the story. Tap the case and it flips open to the cartridge / disc inside
 * (tap again to close). [caseFirst] off (Appearance › Second screen) shows the cartridge / disc first.
 */
@Composable
internal fun CaseDetailsPage(info: SecondHomeGame, caseFirst: Boolean, modifier: Modifier = Modifier) {
    val game = info.game
    if (game == null) {
        NoGameNote()
    } else {
        val tpl = MediaTemplates.of(game.platform)
        val box = if (caseMarker(game.platform) != null) tpl?.box ?: Unit else null
        val hasMedia = tpl?.cart != null || tpl?.label != null || tpl?.disc != null
        val hasCase = box != null || info.caseUrl != null
        // 0 = the case, 1 = what's inside. Starts where the setting says; flips on a tap.
        val flip = remember(game.stableKey) { Animatable(if (caseFirst || !hasMedia) 0f else 1f) }
        LaunchedEffect(game.stableKey, caseFirst, hasMedia) { flip.snapTo(if (caseFirst || !hasMedia) 0f else 1f) }
        val scope = rememberCoroutineScope()
        val canFlip = hasCase && hasMedia
        val density = LocalDensity.current.density
        val meta = info.metadata.shownIn(com.shelf.launcher.data.Features.factsSecond.state.value)
        Box(modifier.fillMaxSize().clip(uiRounded(18.dp))) {
            (info.heroUrl ?: info.wideUrl ?: info.coverUrl)?.let { url ->
                // Blurred once (update 121), not on every redraw of the page over it.
                BakedBlurImage(url, 16.dp, Modifier.fillMaxSize(), alpha = 0.55f)
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.7f)))))
            BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
                val tall = maxHeight > maxWidth * 0.9f
                val mediaW = if (tall) maxWidth else (maxWidth * 0.42f)
                val mediaH = if (tall) maxHeight * 0.46f else maxHeight
                val inspect = info.inspect as? InspectPiece
                val media: @Composable () -> Unit = if (inspect != null) { {
                    // Update 198: the case in 3D: a tap opens it, its disc / cartridge comes out beside it.
                    OpenableCase(inspect, Modifier.width(mediaW).height(mediaH))
                } } else {
                    {
                    Box(
                        Modifier.width(mediaW).height(mediaH)
                            .then(if (canFlip) Modifier.pointerInput(game.stableKey) {
                                detectTapGestures {
                                    scope.launch { flip.animateTo(if (flip.value < 0.5f) 1f else 0f, tween(620, easing = FastOutSlowInEasing)) }
                                }
                            } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        val f = flip.value
                        val showInside = f >= 0.5f
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                // A half turn: the case swings away, the cartridge / disc swings in from behind it.
                                rotationY = if (showInside) (f - 1f) * 180f else f * 180f
                                cameraDistance = 16f * density
                                flatTextFor3d()
                                val lift = 1f - 0.12f * kotlin.math.sin(f * Math.PI.toFloat())
                                scaleX = lift; scaleY = lift
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!showInside && hasCase) {
                                if (box != null) {
                                    // The official case: the cover in the system's cover template with the system's real spine.
                                    CaseArt(game.platform, info.coverUrl, info.box2dUrl, Modifier.fillMaxSize(), gameDisplayName(game.name), 720)
                                }
                                // (Update 202: the scan at its own shape on the see-through stage — nothing drawn round it —
                                // with its file's flat strips trimmed, coverModel / TrimMargins.kt.)
                                else info.caseUrl?.let { AsyncImage(coverModel(it), game.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            } else if (tpl != null && hasMedia) {
                                TemplateMedia(
                                    tpl, labelScan = info.labelUrl, cover = info.coverUrl, modifier = Modifier.fillMaxSize().padding(8.dp),
                                    smallDisc = game.platform == "GameCube" && !com.shelf.launcher.data.Features.bigGcDiscs.state.value
                                )
                            } else {
                                // (Update 202: the cover at its own shape on the see-through stage — no card, colour or bars
                                // round it — with its file's flat strips trimmed, coverModel / TrimMargins.kt.)
                                info.coverUrl?.let { AsyncImage(coverModel(it), game.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                    }
                    }
                }
                val text: @Composable (Modifier) -> Unit = { m ->
                    Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        Text(
                            com.shelf.launcher.data.ConsoleNames.label(game.platform).uppercase(),
                            color = ShelfTheme.accent, fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(gameDisplayName(game.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        val facts = listOfNotNull(
                            meta?.releaseYear?.toString(), meta?.developer, meta?.publisher, meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                            info.playtimeMs?.takeIf { it > 0 }?.let { "Played ${com.shelf.launcher.data.PlaytimeStore.format(it)}" }
                        )
                        if (facts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(facts.joinToString("  ·  "), color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                        }
                        if (VoltUi.on) { Spacer(Modifier.height(8.dp)); VoltageLine(Modifier.width(120.dp)) }
                        Spacer(Modifier.height(8.dp))
                        when {
                            meta?.summary != null -> Text(meta.summary, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp)
                            info.metadataLoading -> Text("Loading the story…", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> Text("No description yet (details come from your details sources, Settings › Accounts).", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                        // Update 202: the game's manual, when it has one — opens in the reader on the main screen.
                        if (rememberHasManual(game)) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "📕  Read the manual",
                                color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(uiRounded(10.dp)).background(Color.White.copy(alpha = 0.1f))
                                    .pointerInput(game.stableKey) { detectTapGestures { com.shelf.launcher.data.ManualLinks.open?.invoke(game) } }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        if (canFlip) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (flip.value < 0.5f) "Tap the case to open it" else "Tap to close the case",
                                color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp
                            )
                        }
                    }
                }
                if (tall) {
                    Column(Modifier.fillMaxSize()) {
                        media()
                        Spacer(Modifier.height(10.dp))
                        text(Modifier.fillMaxWidth().weight(1f))
                    }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        media()
                        Spacer(Modifier.width(16.dp))
                        text(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun NoGameNote() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No game selected.", color = ShelfTheme.textMuted, fontSize = 13.sp)
    }
}
