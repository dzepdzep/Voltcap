package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Update 154: Disc station's player — a portable CD player seen from above, lid open (Disc station › The disc sits in ›
 * Portable CD player, the default). The user found a plain colour (or the blurred art) behind the disc "not doing it".
 * Voltcap's own drawing, no real maker's design or name: a body in charcoal tinted with the game's colour, a hinge
 * across the top, the round well the disc sits in (with the laser's slot), speaker holes and a small LCD with buttons
 * along the front edge. Everything is drawn once per size / colour (drawWithCache); only the disc above it moves.
 */

private val Charcoal = Color(0xFF2B2F36)
private val CharcoalDeep = Color(0xFF15171B)

/** The player's body over the whole panel, tinted a little with [tint] (the game's colour). */
internal fun Modifier.discPlayerBody(tint: Color): Modifier = this.drawWithCache {
    val top = lerp(Charcoal, tint, 0.22f)
    val bottom = lerp(CharcoalDeep, tint, 0.10f)
    val body = Brush.verticalGradient(listOf(top, bottom))
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.10f), 0.35f to Color.Transparent, 1f to Color.Transparent,
        start = Offset.Zero, end = Offset(size.width, size.height)
    )
    val r = 16.dp.toPx()
    val edge = 1.5.dp.toPx()
    // Hinge: a darker band across the top with two knuckles.
    val hingeH = (size.height * 0.035f).coerceIn(5.dp.toPx(), 12.dp.toPx())
    val knuckleW = size.width * 0.16f
    // Speaker holes, bottom right.
    val dot = 1.4.dp.toPx(); val gap = 5.dp.toPx()
    onDrawBehind {
        drawRect(body)
        drawRect(sheen)
        drawRect(Color.Black.copy(alpha = 0.28f), size = Size(size.width, hingeH))
        listOf(0.22f, 0.78f).forEach { fx ->
            drawRoundRect(
                Brush.verticalGradient(listOf(lerp(top, Color.White, 0.18f), lerp(top, Color.Black, 0.25f)), 0f, hingeH * 1.6f),
                topLeft = Offset(size.width * fx - knuckleW / 2, 0f), size = Size(knuckleW, hingeH * 1.6f),
                cornerRadius = CornerRadius(hingeH * 0.6f)
            )
        }
        for (row in 0 until 3) for (col in 0 until 5) {
            drawCircle(
                Color.Black.copy(alpha = 0.38f), dot,
                Offset(size.width - r - col * gap, size.height - r * 0.9f - row * gap)
            )
        }
        // A light edge all round, like a moulded rim.
        drawRoundRect(
            Color.White.copy(alpha = 0.12f), topLeft = Offset(edge / 2, edge / 2),
            size = Size(size.width - edge, size.height - edge), cornerRadius = CornerRadius(r), style = Stroke(edge)
        )
    }
}

/**
 * The round well the disc sits in, in a box the disc's size: [frac] of the box is the disc (a GameCube disc is
 * smaller). A recess a little wider than the disc, the laser's slot from the middle towards the front, a lit rim.
 */
internal fun Modifier.discWell(tint: Color, frac: Float = 1f): Modifier = this.drawWithCache {
    val c = Offset(size.width / 2, size.height / 2)
    val discR = size.minDimension / 2 * frac
    val wellR = discR * 1.07f
    val recess = Brush.radialGradient(
        0f to Color(0xFF0C0D10), 0.75f to Color(0xFF16181D), 1f to lerp(Color(0xFF23262C), tint, 0.15f),
        center = c, radius = wellR
    )
    // The laser's slot: a rounded bar from near the hub to the rim, down and to the right.
    val slotW = discR * 0.16f
    val slot = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = -slotW / 2, top = discR * 0.28f, right = slotW / 2, bottom = discR * 0.98f,
                cornerRadius = CornerRadius(slotW / 2)
            )
        )
    }
    val rim = 2.dp.toPx()
    onDrawBehind {
        drawCircle(recess, wellR, c)
        withTransform({ translate(c.x, c.y); rotate(-35f, Offset.Zero) }) {
            drawPath(slot, Color.Black.copy(alpha = 0.65f))
            drawPath(slot, lerp(Color(0xFF3A3F47), tint, 0.25f).copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
        }
        // Spindle under the disc (shows while discs change).
        drawCircle(Color(0xFF5A6068), discR * 0.09f, c)
        drawCircle(Color(0xFF2A2E34), discR * 0.04f, c)
        // Rim: light outside, dark inside — a recess.
        drawCircle(Color.White.copy(alpha = 0.16f), wellR + rim / 2, c, style = Stroke(rim))
        drawCircle(Color.Black.copy(alpha = 0.45f), wellR - rim / 2, c, style = Stroke(rim))
    }
}

/** The player's front edge: a small LCD saying the disc is loaded, and its buttons. */
@Composable
internal fun DiscPlayerFront(tint: Color, compact: Boolean, modifier: Modifier = Modifier) {
    val lcdBack = lerp(Color(0xFF17221B), tint, 0.18f)
    val lcdText = lerp(Color(0xFFB8F5C8), tint, 0.35f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(lerp(lcdBack, Color.Black, 0.2f), lcdBack)))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 4.dp)
        ) {
            Text(
                "DISC LOADED", color = lcdText, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, maxLines = 1
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        val b = if (compact) 16.dp else 20.dp
        listOf(0, 1, 2).forEach { kind ->
            Canvas(Modifier.padding(horizontal = 2.dp).size(b)) {
                drawCircle(Brush.verticalGradient(listOf(Color(0xFF4A5059), Color(0xFF23272D))))
                drawCircle(Color.White.copy(alpha = 0.14f), style = Stroke(1.dp.toPx()))
                val g = Color.White.copy(alpha = 0.75f)
                val s = size.minDimension
                when (kind) {
                    // Back: two small triangles pointing left.
                    0 -> listOf(0.30f, 0.52f).forEach { x0 ->
                        drawPath(Path().apply {
                            moveTo(s * (x0 + 0.20f), s * 0.34f); lineTo(s * x0, s * 0.5f); lineTo(s * (x0 + 0.20f), s * 0.66f); close()
                        }, g)
                    }
                    // Play.
                    1 -> drawPath(Path().apply { moveTo(s * 0.40f, s * 0.30f); lineTo(s * 0.70f, s * 0.5f); lineTo(s * 0.40f, s * 0.70f); close() }, tint.copy(alpha = 0.95f))
                    // Stop.
                    else -> drawRect(g, topLeft = Offset(s * 0.35f, s * 0.35f), size = Size(s * 0.30f, s * 0.30f))
                }
            }
        }
    }
}
