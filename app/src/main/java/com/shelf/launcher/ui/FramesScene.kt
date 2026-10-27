package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * A theme scene that is an animated pixel-art picture: [res] holds its [frames] side by side (one strip, made
 * from the original GIF), shown one after another every [frameMs], filling the screen (cropped, centred) with
 * no smoothing so the pixels stay crisp. Holds still behind dialogs, while idle (deep), and with Lite effects
 * unless "Animate on Lite" is on. Only the drawing changes from frame to frame.
 */
@Composable
internal fun FramesScene(res: Int, frames: Int, frameMs: Long, modifier: Modifier = Modifier) {
    val strip: ImageBitmap = com.shelf.launcher.ui.cachedImageResource(res)
    var frame by remember { mutableIntStateOf(0) }
    val lite = LocalLiteEffects.current && !LocalAnimateOnLite.current
    val moving = !lite && !LocalSceneCovered.current && !HomeIdle.still && rememberAnimationsEnabled()
    LaunchedWhileActive(moving, frames, frameMs) {
        if (!moving) return@LaunchedWhileActive
        while (true) {
            kotlinx.coroutines.delay(frameMs)
            frame = (frame + 1) % frames
        }
    }
    Canvas(modifier) {
        val fw = strip.width / frames
        val fh = strip.height
        val k = maxOf(size.width / fw, size.height / fh)
        val dw = (fw * k).roundToInt(); val dh = (fh * k).roundToInt()
        drawImage(
            strip,
            srcOffset = IntOffset(fw * frame, 0), srcSize = IntSize(fw, fh),
            dstOffset = IntOffset(((size.width - dw) / 2).roundToInt(), ((size.height - dh) / 2).roundToInt()),
            dstSize = IntSize(dw, dh),
            filterQuality = FilterQuality.None
        )
    }
}
