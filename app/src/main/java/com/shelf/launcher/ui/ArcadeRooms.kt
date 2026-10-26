package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.shelf.launcher.data.Features
import kotlin.math.max

/*
 * Update 348: the arcade's rooms (Layout options › Room). Toni: "machines standing on an abstract grid" — so beside the
 * neon stage (kept), rooms you'd recognise: a 1990s arcade (dark painted walls, recessed lights, a row of machines along
 * the back wall, the cosmic carpet), a game centre (cool grey walls, fluorescent ceiling panels, candy cabinets along the
 * back, a vinyl tile floor with the lights' sheen), a dark neon arcade (black walls, neon tubes, a glossy floor that
 * mirrors them and the machines) and a showroom (calm grey walls under spotlights, polished concrete). All drawn once
 * (a static layer behind the machines; nothing moves), the wall meeting the floor at [horizon]. Original shapes only.
 */

/** The wall's top / floor's bottom colours round the layout (behind the status bar's strip). */
internal fun arcadeRoomEdges(room: Features.ArRoom): Pair<Color, Color> = when (room) {
    Features.ArRoom.NEON -> Color(0xFF120A2A) to Color(0xFF160C2C)
    Features.ArRoom.CARPET -> Color(0xFF0B0A0E) to Color(0xFF1B1E48)
    Features.ArRoom.CENTER -> Color(0xFF6C7078) to Color(0xFF474A51)
    Features.ArRoom.DARK -> Color(0xFF050507) to Color(0xFF0A0A0E)
    Features.ArRoom.SHOWROOM -> Color(0xFF34363B) to Color(0xFF3B3D42)
    Features.ArRoom.THEME -> Color.Transparent to Color.Transparent
}

/** How much the floor mirrors the machines (0: not at all). */
internal fun arcadeRoomGloss(room: Features.ArRoom): Float = when (room) {
    Features.ArRoom.DARK -> 0.7f
    Features.ArRoom.SHOWROOM -> 0.5f
    Features.ArRoom.CENTER -> 0.22f
    else -> 0f
}

/**
 * The room, the wall meeting the floor at [horizon] (share of the height); [accent] the machine you're on's colour. [calm]:
 * behind text (Showcase — the game's name and buttons stand on the wall): the back row lower and dimmer, no wall signs.
 */
internal fun DrawScope.drawArcadeRoom(room: Features.ArRoom, horizon: Float, accent: Color, calm: Boolean = false) {
    val hy = size.height * horizon
    when (room) {
        Features.ArRoom.NEON -> neonStage(hy, accent)
        Features.ArRoom.CARPET -> nineties(hy, accent, calm)
        Features.ArRoom.CENTER -> gameCentre(hy, calm)
        Features.ArRoom.DARK -> darkNeon(hy, accent, calm)
        Features.ArRoom.SHOWROOM -> showroom(hy)
        Features.ArRoom.THEME -> themeRoom(hy)
    }
    // Where the wall meets the floor.
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startY = hy, endY = hy + size.height * 0.035f), Offset(0f, hy), Size(size.width, size.height * 0.035f))
}

/** A tiny repeatable "random" number for [k] (0…1), so the room is the same every time it's drawn. */
private fun hash(k: Int, salt: Int = 0): Float {
    var x = k * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** The floor from the horizon to the bottom, its colour going from [far] to [near]. */
private fun DrawScope.floor(hy: Float, far: Color, near: Color) {
    drawRect(Brush.verticalGradient(listOf(far, near), startY = hy, endY = size.height), Offset(0f, hy), Size(size.width, size.height - hy))
}

/** Depth on the floor: the screen y of a floor line [q] of the way from the horizon (0) to the nearest (1), in perspective. */
private fun floorY(hy: Float, h: Float, q: Float): Float = hy + (h - hy) * q

/**
 * A row of machines along the back wall: dark silhouettes with lit screens and marquees, standing on the floor line —
 * the room's depth behind the ones you browse. [candy]: rounded Japanese tops; [body] their colour.
 */
private fun DrawScope.backRow(hy: Float, mh: Float, body: Color, candy: Boolean, glowA: Float) {
    val mw = mh * (if (candy) 0.5f else 0.4f)
    val gap = mw * 0.18f
    var x = -mw * 0.3f
    var k = 0
    val hues = listOf(Color(0xFF3DE7FF), Color(0xFFFF4FB0), Color(0xFFFFD34A), Color(0xFF7CFF6B), Color(0xFFB07CFF), Color(0xFFFF8A3D))
    while (x < size.width) {
        val hue = hues[(hash(k, 3) * hues.size).toInt().coerceIn(0, hues.size - 1)]
        val top = hy - mh
        val path = Path().apply {
            if (candy) {
                moveTo(x, hy); lineTo(x, top + mh * 0.22f); quadraticBezierTo(x + mw * 0.05f, top, x + mw * 0.3f, top)
                lineTo(x + mw * 0.7f, top); quadraticBezierTo(x + mw * 0.95f, top, x + mw, top + mh * 0.22f); lineTo(x + mw, hy); close()
            } else {
                moveTo(x, hy); lineTo(x, top); lineTo(x + mw, top); lineTo(x + mw, hy); close()
            }
        }
        // A soft pool of its screen's light on the wall.
        drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = 0.10f * glowA), Color.Transparent), center = Offset(x + mw / 2f, top + mh * 0.4f), radius = mw * 1.1f), mw * 1.1f, Offset(x + mw / 2f, top + mh * 0.4f))
        drawPath(path, body)
        // Its side panel's edge (the machines are turned a touch to the room).
        drawRect(lerp(body, Color.Black, 0.35f), Offset(x + mw * 0.9f, top + mh * 0.05f), Size(mw * 0.1f, mh * 0.95f))
        // Marquee, screen, deck.
        val mq = if (candy) top + mh * 0.1f else top + mh * 0.04f
        drawRect(hue.copy(alpha = 0.55f * glowA), Offset(x + mw * 0.1f, mq), Size(mw * 0.72f, mh * 0.08f))
        val sy = mq + mh * 0.12f
        drawRect(Color(0xFF07080B), Offset(x + mw * 0.08f, sy), Size(mw * 0.76f, mh * 0.27f))
        drawRect(Brush.verticalGradient(listOf(hue.copy(alpha = 0.55f * glowA), lerp(hue, Color.Black, 0.6f).copy(alpha = 0.5f * glowA)), startY = sy, endY = sy + mh * 0.25f),
            Offset(x + mw * 0.12f, sy + mh * 0.02f), Size(mw * 0.68f, mh * 0.23f))
        drawRect(lerp(body, Color.White, 0.08f), Offset(x + mw * 0.02f, sy + mh * 0.3f), Size(mw * 0.9f, mh * 0.05f))
        x += mw + gap + mw * 0.25f * hash(k, 9)
        k++
    }
}

// ---- The rooms ----

/** The neon stage (Update 347): a dark violet room, three neon tubes high on the wall, the stage's grid floor and its carpet dots. */
private fun DrawScope.neonStage(hy: Float, accent: Color) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF120A2A), Color(0xFF1C0F3A)), endY = hy), size = Size(size.width, hy))
    val tubes = listOf(accent, Color(0xFF3DE7FF), Color(0xFFFF3DAE))
    for ((n, col) in tubes.withIndex()) {
        val y = size.height * (0.035f + n * 0.03f)
        val p = Path().apply {
            moveTo(size.width * (0.03f + n * 0.03f), y)
            var x = size.width * (0.03f + n * 0.03f); var up = true
            while (x < size.width * (0.5f - n * 0.04f)) { x += size.width * 0.03f; lineTo(x, y + (if (n == 1) (if (up) -1f else 1f) * size.height * 0.008f else 0f)); up = !up }
        }
        drawPath(p, col.copy(alpha = 0.16f), style = Stroke(hy * 0.025f))
        drawPath(p, col.copy(alpha = 0.7f), style = Stroke(max(1.5f, hy * 0.005f)))
    }
    for (l in 0 until 5) {
        val cx = size.width * (l + 0.5f) / 5f
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), center = Offset(cx, 0f), radius = size.width * 0.18f),
            Offset(cx - size.width * 0.18f, 0f), Size(size.width * 0.36f, hy))
    }
    floor(hy, Color(0xFF0B0716), Color(0xFF160C2C))
    val vp = Offset(size.width / 2f, hy - size.height * 0.25f)
    val seam = Color.White.copy(alpha = 0.05f)
    for (i in -10..10) {
        val x = size.width / 2f + i * size.width * 0.12f
        val t = (hy - vp.y) / (size.height - vp.y)
        drawLine(seam, Offset(vp.x + (x - vp.x) * t, hy), Offset(x, size.height), 1.5f)
    }
    var y = hy; var step = size.height * 0.012f
    while (y < size.height) { drawLine(seam, Offset(0f, y), Offset(size.width, y), 1f); y += step; step *= 1.35f }
    val dots = listOf(Color(0xFF3DE7FF), Color(0xFFFF3DAE), Color(0xFFFFE15A), accent)
    for (k in 0 until 140) {
        val fx = ((k * 73) % 101) / 101f; val fy = ((k * 37) % 89) / 89f
        val yy = hy + (size.height - hy) * fy * fy
        drawCircle(dots[k % dots.size].copy(alpha = 0.18f), 1.5f + 3.5f * fy, Offset(size.width * fx, yy))
    }
}

/** A 1990s arcade: a dark ceiling with recessed lights, painted walls, machines along the back, the cosmic carpet. */
private fun DrawScope.nineties(hy: Float, accent: Color, calm: Boolean) {
    val cy = hy * 0.15f
    // Ceiling: black tiles, warm recessed lights.
    drawRect(Color(0xFF0B0A0E), size = Size(size.width, cy))
    for (i in 1..8) drawLine(Color.White.copy(alpha = 0.035f), Offset(size.width * i / 9f, 0f), Offset(size.width * i / 9f, cy), 1f)
    for (i in 0 until 7) {
        val c = Offset(size.width * (i + 0.5f) / 7f, cy * 0.55f)
        drawOval(Color(0xFFFFE2B0).copy(alpha = 0.8f), Offset(c.x - cy * 0.18f, c.y - cy * 0.07f), Size(cy * 0.36f, cy * 0.14f))
        // Its light falling down the wall.
        drawRect(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.10f), Color.Transparent), center = Offset(c.x, cy), radius = hy * 0.55f),
            Offset(c.x - hy * 0.55f, cy), Size(hy * 1.1f, hy - cy))
    }
    // The wall: dark plum paint above a darker wainscot, a lit trim strip between.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF231A26), Color(0xFF2C2030)), startY = cy, endY = hy), Offset(0f, cy), Size(size.width, hy - cy))
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(0f, cy), Size(size.width, max(2f, hy * 0.012f)))
    for (l in 0 until 7) {
        val c = Offset(size.width * (l + 0.5f) / 7f, cy)
        drawRect(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.07f), Color.Transparent), center = c, radius = hy * 0.5f), Offset(c.x - hy * 0.5f, cy), Size(hy, hy - cy))
    }
    // A neon sign high on the wall: a lightning bolt and a ring (original shapes, no words).
    if (!calm) {
    val sx = size.width * 0.12f; val sy = cy + (hy - cy) * 0.12f; val s = (hy - cy) * 0.16f
    val bolt = Path().apply { moveTo(sx, sy); lineTo(sx + s * 0.5f, sy); lineTo(sx + s * 0.25f, sy + s * 0.45f); lineTo(sx + s * 0.6f, sy + s * 0.45f); lineTo(sx + s * 0.05f, sy + s * 1.1f); lineTo(sx + s * 0.22f, sy + s * 0.6f); lineTo(sx - s * 0.1f, sy + s * 0.6f); close() }
    drawPath(bolt, Color(0xFFFFD34A).copy(alpha = 0.18f), style = Stroke(s * 0.22f))
    drawPath(bolt, Color(0xFFFFE58A), style = Stroke(max(1.5f, s * 0.05f)))
    drawCircle(Color(0xFFFF4FB0).copy(alpha = 0.18f), s * 0.42f, Offset(sx + s * 1.1f, sy + s * 0.55f), style = Stroke(s * 0.2f))
    drawCircle(Color(0xFFFF8AD0), s * 0.42f, Offset(sx + s * 1.1f, sy + s * 0.55f), style = Stroke(max(1.5f, s * 0.05f)))
    }
    // Machines along the back wall.
    backRow(hy, (hy - cy) * (if (calm) 0.28f else 0.5f), Color(0xFF141318), candy = false, glowA = if (calm) 0.45f else 0.9f)
    // The carpet: deep blue with a scatter of neon shapes, smaller with distance.
    floor(hy, Color(0xFF141737), Color(0xFF1D2150))
    val colours = listOf(Color(0xFF3DE7FF), Color(0xFFFF4FB0), Color(0xFFFFD34A), lerp(accent, Color.White, 0.2f), Color(0xFF7CFF6B))
    for (k in 0 until 220) {
        val q = hash(k, 1); val fx = hash(k, 2)
        val y = floorY(hy, size.height, q * q)
        val sz = (2f + 11f * q * q) * size.height / 1080f
        val col = colours[k % colours.size].copy(alpha = 0.16f + 0.18f * q)
        val x = size.width * fx
        when (k % 4) {
            0 -> drawCircle(col, sz * 0.5f, Offset(x, y), style = Stroke(max(1f, sz * 0.22f)))
            1 -> drawPath(Path().apply { moveTo(x, y - sz * 0.5f); lineTo(x + sz * 0.55f, y + sz * 0.4f); lineTo(x - sz * 0.55f, y + sz * 0.4f); close() }, col)
            2 -> drawPath(Path().apply { moveTo(x - sz, y); quadraticBezierTo(x - sz * 0.5f, y - sz * 0.6f, x, y); quadraticBezierTo(x + sz * 0.5f, y + sz * 0.6f, x + sz, y) }, col, style = Stroke(max(1f, sz * 0.2f)))
            else -> drawCircle(col, sz * 0.18f, Offset(x, y))
        }
    }
    // The carpet's pile darkening into the distance.
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startY = hy, endY = floorY(hy, size.height, 0.35f)), Offset(0f, hy), Size(size.width, size.height - hy))
}

/** A Japanese game centre: fluorescent ceiling panels, cool grey walls, candy cabinets along the back, vinyl tiles. */
private fun DrawScope.gameCentre(hy: Float, calm: Boolean) {
    val cy = hy * 0.2f
    // The ceiling: light grey, rows of fluorescent panels getting smaller towards the back.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7D8189), Color(0xFF6A6E76)), endY = cy), size = Size(size.width, cy))
    for (row in 0 until 3) {
        val y = cy * (0.12f + row * 0.32f); val ph = cy * (0.16f - row * 0.035f)
        val n = 5 + row
        for (i in 0 until n) {
            val w = size.width / n * 0.42f
            val x = size.width * (i + 0.5f) / n - w / 2f
            drawRect(Color.White.copy(alpha = 0.10f), Offset(x - w * 0.15f, y - ph * 0.5f), Size(w * 1.3f, ph * 2f))
            drawRect(Color(0xFFF4F7FF).copy(alpha = 0.92f - row * 0.15f), Offset(x, y), Size(w, ph))
        }
    }
    // The wall: cool grey, a coloured band at head height, hanging signs (coloured boards with white bars, no words).
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5E626A), Color(0xFF4C5058)), startY = cy, endY = hy), Offset(0f, cy), Size(size.width, hy - cy))
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, cy), Size(size.width, max(2f, hy * 0.01f)))
    drawRect(Color(0xFFE8445A).copy(alpha = 0.55f), Offset(0f, cy + (hy - cy) * 0.36f), Size(size.width, (hy - cy) * 0.025f))
    val signs = listOf(Color(0xFFFFC93A), Color(0xFF39C6F4), Color(0xFFFF5C8A))
    if (!calm) for ((i, c) in signs.withIndex()) {
        val w = size.width * 0.11f; val h = (hy - cy) * 0.12f
        val x = size.width * (0.18f + i * 0.31f); val y = cy + (hy - cy) * 0.08f
        drawLine(Color(0xFF2A2C31), Offset(x + w * 0.2f, cy), Offset(x + w * 0.2f, y), 1.5f)
        drawLine(Color(0xFF2A2C31), Offset(x + w * 0.8f, cy), Offset(x + w * 0.8f, y), 1.5f)
        drawRect(c, Offset(x, y), Size(w, h))
        drawRect(Color.Black.copy(alpha = 0.18f), Offset(x, y + h * 0.82f), Size(w, h * 0.18f))
        for (b in 0 until 3) drawRect(Color.White.copy(alpha = 0.85f), Offset(x + w * (0.1f + b * 0.29f), y + h * 0.3f), Size(w * 0.22f, h * 0.36f))
    }
    backRow(hy, (hy - cy) * (if (calm) 0.28f else 0.48f), Color(0xFF8F8C85), candy = true, glowA = if (calm) 0.4f else 0.8f)
    // Vinyl tiles in perspective: two greys, and the ceiling lights' sheen on them.
    floor(hy, Color(0xFF3F4249), Color(0xFF4A4D55))
    val vp = Offset(size.width / 2f, hy)
    val cols = 14
    val rows = 9
    for (r in 0 until rows) {
        val q0 = (r / rows.toFloat()).let { it * it }; val q1 = ((r + 1) / rows.toFloat()).let { it * it }
        val y0 = floorY(hy, size.height, q0); val y1 = floorY(hy, size.height, q1)
        for (c in -cols..cols) {
            if ((r + c) % 2 == 0) continue
            val xb0 = vp.x + c * size.width * 0.16f; val xb1 = vp.x + (c + 1) * size.width * 0.16f
            val p = Path().apply {
                moveTo(vp.x + (xb0 - vp.x) * q0, y0); lineTo(vp.x + (xb1 - vp.x) * q0, y0)
                lineTo(vp.x + (xb1 - vp.x) * q1, y1); lineTo(vp.x + (xb0 - vp.x) * q1, y1); close()
            }
            drawPath(p, Color(0xFF54575F).copy(alpha = 0.55f))
        }
    }
    for (i in 0 until 6) {
        val x = size.width * (i + 0.5f) / 6f
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), startY = hy, endY = floorY(hy, size.height, 0.6f)),
            Offset(x - size.width * 0.03f, hy), Size(size.width * 0.06f, size.height - hy))
    }
}

/** A dark neon arcade: black walls, neon tubes, a haze, and a glossy floor that mirrors the tubes. */
private fun DrawScope.darkNeon(hy: Float, accent: Color, calm: Boolean) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050507), Color(0xFF0B0A12)), endY = hy), size = Size(size.width, hy))
    // A violet haze in the middle of the room.
    drawRect(Brush.radialGradient(listOf(Color(0xFF6B2BFF).copy(alpha = 0.12f), Color.Transparent), center = Offset(size.width / 2f, hy * 0.75f), radius = size.width * 0.45f), size = Size(size.width, hy))
    val tubeW = max(2f, size.height * 0.004f)
    val tubes = ArrayList<Pair<Path, Color>>()
    // Upright tubes along the wall, a frame high in the middle, a line at the foot of the wall.
    for ((i, c) in listOf(Color(0xFFFF3DAE), Color(0xFF3DE7FF), Color(0xFFFF3DAE), Color(0xFF3DE7FF)).withIndex()) {
        val x = size.width * (0.06f + i * 0.29f)
        tubes += Path().apply { moveTo(x, hy * 0.18f); lineTo(x, hy * 0.82f) } to c
    }
    val fw = size.width * 0.22f
    tubes += Path().apply { val x0 = size.width * 0.62f; val y0 = hy * 0.12f; moveTo(x0, y0); lineTo(x0 + fw, y0); lineTo(x0 + fw, y0 + hy * 0.12f); lineTo(x0, y0 + hy * 0.12f); close() } to lerp(accent, Color.White, 0.15f)
    tubes += Path().apply { moveTo(0f, hy - tubeW * 3f); lineTo(size.width, hy - tubeW * 3f) } to Color(0xFF3DE7FF)
    for ((p, c) in tubes) {
        drawPath(p, c.copy(alpha = 0.14f), style = Stroke(tubeW * 7f))
        drawPath(p, c.copy(alpha = 0.95f), style = Stroke(tubeW))
    }
    backRow(hy, hy * (if (calm) 0.22f else 0.42f), Color(0xFF0D0D12), candy = false, glowA = if (calm) 0.5f else 1f)
    // The glossy floor, the tubes mirrored in it, fading.
    floor(hy, Color(0xFF060609), Color(0xFF0C0C11))
    for (i in 0 until 4) {
        val x = size.width * (0.06f + i * 0.29f)
        val c = if (i % 2 == 0) Color(0xFFFF3DAE) else Color(0xFF3DE7FF)
        drawRect(Brush.verticalGradient(listOf(c.copy(alpha = 0.22f), Color.Transparent), startY = hy, endY = hy + (hy * 0.64f)), Offset(x - tubeW * 2f, hy), Size(tubeW * 4f, hy * 0.64f))
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3DE7FF).copy(alpha = 0.10f), Color.Transparent), startY = hy, endY = hy + size.height * 0.08f), Offset(0f, hy), Size(size.width, size.height * 0.08f))
}

/** A showroom: calm grey walls under spotlights, a skirting line, polished concrete. */
private fun DrawScope.showroom(hy: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF34363B), Color(0xFF45474C)), endY = hy), size = Size(size.width, hy))
    // The track lights and their pools down the wall.
    drawRect(Color(0xFF1E1F23), size = Size(size.width, max(3f, hy * 0.02f)))
    for (i in 0 until 5) {
        val x = size.width * (i + 0.5f) / 5f
        drawRect(Color(0xFF15161A), Offset(x - hy * 0.015f, 0f), Size(hy * 0.03f, hy * 0.05f))
        val pool = Path().apply { moveTo(x - hy * 0.02f, hy * 0.05f); lineTo(x + hy * 0.02f, hy * 0.05f); lineTo(x + hy * 0.3f, hy); lineTo(x - hy * 0.3f, hy); close() }
        drawPath(pool, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)), startY = hy * 0.05f, endY = hy))
        drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), center = Offset(x, hy * 0.9f), radius = hy * 0.3f), Offset(x - hy * 0.3f, hy * 0.75f), Size(hy * 0.6f, hy * 0.3f))
    }
    // Skirting.
    drawRect(Color(0xFF6A6D73), Offset(0f, hy - max(2f, hy * 0.012f)), Size(size.width, max(2f, hy * 0.012f)))
    // Polished concrete: lighter close up, faint mottling, the spotlights' reflections.
    floor(hy, Color(0xFF2B2D31), Color(0xFF3A3C41))
    for (k in 0 until 60) {
        val q = hash(k, 5); val x = size.width * hash(k, 6)
        val y = floorY(hy, size.height, q)
        val r = (8f + 60f * q) * size.height / 1080f
        drawOval(Color.Black.copy(alpha = 0.035f), Offset(x - r, y - r * 0.3f), Size(r * 2f, r * 0.6f))
    }
    for (i in 0 until 5) {
        val x = size.width * (i + 0.5f) / 5f
        drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), center = Offset(x, hy + (size.height - hy) * 0.18f), radius = hy * 0.3f),
            Offset(x - hy * 0.12f, hy), Size(hy * 0.24f, (size.height - hy) * 0.4f))
    }
}

/** Your theme behind: only a darker floor under the machines so they stand somewhere. */
private fun DrawScope.themeRoom(hy: Float) {
    floor(hy, Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.55f))
    var y = hy; var step = size.height * 0.012f
    while (y < size.height) { drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1f); y += step; step *= 1.35f }
}
