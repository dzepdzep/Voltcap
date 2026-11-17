package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * P2 batch 1 (681d): the Console hub window's own pages. The window is the console's brushed-metal nameplate with its game
 * seated beside it, so its pages are the plate's other faces:
 *  - Details      = ENGRAVED: each fact stamped on its own little metal tag (the label engraved small, the value cut deep),
 *                   the story set into the plate's back panel - a recessed pane (a D-pad stop that scrolls).
 *  - Achievements = SERVICE MEDALS: the medals you hold pinned in a row along the plate's top edge like its screws (a set too
 *                   long for the row ends in "+N"), the record below as riveted strips - a medal for one you have, an empty
 *                   mounting ring for one still to get - opened with A for the whole citation, date and rarity.
 *  - Options      = SWITCH PANEL: each option a toggle with its engraved label plate and an indicator lamp lit in the hub's
 *                   colour while it has the focus; one that removes something sits under a red flip cover and asks twice.
 * Motion: the plate flips on its top hinge - the face going away swings up, the next swings down into place (none with
 * Motion Off). Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

internal val HUB_ENGRAVE = Color(0xFFD7DEE8)
private val HUB_COVER_RED = Color(0xFFD9443F)

/** The plate's page name, engraved beside the console name. Pure. */
internal fun hubPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "NAMEPLATE"
    WinPage.DETAILS -> "ENGRAVED"
    WinPage.TROPHIES -> "SERVICE MEDALS"
    WinPage.OPTIONS -> "SWITCH PANEL"
    WinPage.PERF -> "PERFORMANCE"
    else -> p.name
}

/** How many medals fit on the plate's edge for [room] slots: the medals to pin and the "+N" left over. Pure. */
internal fun hubMedalRow(earned: Int, room: Int): Pair<Int, Int> {
    if (earned <= 0 || room <= 0) return 0 to earned.coerceAtLeast(0)
    if (earned <= room) return earned to 0
    return (room - 1).coerceAtLeast(0) to earned - (room - 1).coerceAtLeast(0)
}

internal fun hubMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    fadeIn(tween(NATIVE_PAGE_MS / 3, delayMillis = NATIVE_PAGE_MS / 3)) togetherWith fadeOut(tween(NATIVE_PAGE_MS / 2))

private fun inkOnHub(c: Color) = if (c.luminance() > 0.45f) Color(0xFF15171C) else Color.White

/** A recessed (engraved) panel in the plate. */
private fun Modifier.hubRecess(shape: RoundedCornerShape = RoundedCornerShape(6.dp)): Modifier =
    this.clip(shape).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.22f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.White.copy(alpha = 0.18f))), shape)

@Composable
internal fun AnimatedVisibilityScope.HubPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, modifier: Modifier) {
    val anim = rememberAnimationsEnabled()
    // The hinge: a face swings down into place from the plate's top edge, and the one leaving swings up out of the way.
    val swing by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing) else snap() }, label = "hubHinge") {
        if (it == EnterExitState.Visible) 0f else 78f
    }
    Column(modifier.graphicsLayer {
        rotationX = swing; transformOrigin = TransformOrigin(0.5f, 0f); cameraDistance = 14f * density
    }) {
        Text(hubPageName(p), color = ctx.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
        WinFullText(ctx.name, color = Color.White, fontSize = 16.sp, maxLines = 2, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
        Spacer(Modifier.height(6.dp))
        val body = Modifier.weight(1f).fillMaxWidth()
        when (p) {
            WinPage.DETAILS -> HubEngraved(ctx, live, body)
            WinPage.TROPHIES -> HubMedals(ctx, live, body)
            WinPage.OPTIONS -> HubSwitches(ctx, live, body)
            else -> ctx.pageFor(p, body, live) // (Performance: the shared page - not a page of this plate yet)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HubEngraved(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(HUB_ENGRAVE)) {
        val facts = nativeFacts(ctx, setOf("Console", "Year", "Trophies"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            facts.forEach { f ->
                // The stamped tag: two rivets, the label engraved small, the value cut deep.
                Column(Modifier.hubRecess(RoundedCornerShape(4.dp)).drawBehind {
                    val r = 1.6.dp.toPx(); val m = 4.dp.toPx()
                    drawCircle(Color(0xFF8A919C), r, Offset(m, m)); drawCircle(Color(0xFF8A919C), r, Offset(size.width - m, m))
                }.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 5.dp)) {
                    Text(f.label.uppercase(), color = HUB_ENGRAVE.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(f.value, color = HUB_ENGRAVE, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().hubRecess(RoundedCornerShape(8.dp)).then(if (on) Modifier.border(2.dp, a, RoundedCornerShape(8.dp)) else Modifier).padding(10.dp)) {
                Text("THE PLATE'S BACK", color = HUB_ENGRAVE.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(3.dp))
                Text(ctx.summary ?: "Nothing engraved here yet - this game has no story.", color = HUB_ENGRAVE.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun HubMedalDisc(url: String?, earned: Boolean, a: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(if (earned) Color.Black else Color.Black.copy(alpha = 0.35f))
        .border(if (earned) 2.dp else 1.dp, if (earned) a else Color(0xFF8A919C).copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
        if (earned) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape))
        else Box(Modifier.size(size * 0.35f).clip(CircleShape).background(Color(0xFF15171B)).border(1.dp, Color(0xFF6A707A), CircleShape))
    }
}

@Composable
private fun HubMedals(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // The medals pinned along the plate's edge, the count stamped at its end.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slot = 30.dp
            val room = ((maxWidth - 92.dp) / slot).toInt().coerceAtLeast(1)
            val (pinned, more) = hubMedalRow(t.earned, room)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    t.gained.take(pinned).forEach { HubMedalDisc(com.shelf.launcher.data.RaBadges.url(it.badgeName), true, a, 26.dp) }
                    if (more > 0) Text("+$more", color = HUB_ENGRAVE, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    if (t.earned == 0) Text(if (t.total > 0) "No medals yet" else "No medals for this game", color = HUB_ENGRAVE.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Text("${t.earned}/${t.total}", color = HUB_ENGRAVE, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.hubRecess(RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Row(Modifier.padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SERVICE RECORD", color = HUB_ENGRAVE.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            NativeItem(onClick = t.nextOrder, Modifier, focus = focus, live = live) { on ->
                Text("ORDER: " + t.order.label.uppercase(), color = if (on) inkOnHub(a) else HUB_ENGRAVE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) a else Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, if (on) a else Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(HUB_ENGRAVE), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            t.list.forEach { ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        // The riveted strip.
                        Column(Modifier.fillMaxWidth().hubRecess(RoundedCornerShape(5.dp))
                            .then(if (on) Modifier.border(2.dp, a, RoundedCornerShape(5.dp)) else Modifier)
                            .drawBehind {
                                val r = 1.5.dp.toPx(); val m = 4.dp.toPx()
                                drawCircle(Color(0xFF8A919C), r, Offset(size.width - m, m)); drawCircle(Color(0xFF8A919C), r, Offset(size.width - m, size.height - m))
                            }.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HubMedalDisc(com.shelf.launcher.data.RaBadges.url(ach.badgeName), earned, a, 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(ach.title ?: "Untitled", color = if (earned) Color.White else HUB_ENGRAVE.copy(alpha = 0.7f), fontSize = 13.sp,
                                    fontWeight = if (on) FontWeight.Black else FontWeight.Bold, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                Text(ach.points?.let { "$it PTS" } ?: "-", color = if (earned) a else HUB_ENGRAVE.copy(alpha = 0.5f), fontSize = 10.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = HUB_ENGRAVE.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                                else WinFullText(d, color = HUB_ENGRAVE.copy(alpha = 0.62f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp,
                                    modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "awarded " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f %% of players".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("No service record - this game has no achievements.", color = HUB_ENGRAVE.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun HubSwitches(ctx: WinSceneCtx, live: Boolean, modifier: Modifier) {
    val a = ctx.accent
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(HUB_ENGRAVE), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Row(Modifier.fillMaxWidth().hubRecess().then(if (on) Modifier.border(2.dp, if (o.destructive) HUB_COVER_RED else a, RoundedCornerShape(6.dp)) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The indicator lamp.
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) (if (o.destructive) HUB_COVER_RED else a) else Color(0xFF15171B))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    // The toggle: a metal bat lever in its collar - up while focused; a red flip cover over the ones that remove something.
                    Canvas(Modifier.size(width = 18.dp, height = 26.dp)) {
                        val c = Offset(size.width / 2, size.height / 2)
                        drawCircle(Color(0xFF1B1E23), size.width * 0.42f, c)
                        drawCircle(Color(0xFF8A919C), size.width * 0.42f, c, style = Stroke(1.dp.toPx()))
                        val up = on || armed
                        drawLine(Color(0xFFC9CED6), c, Offset(c.x, if (up) 2.dp.toPx() else size.height - 2.dp.toPx()), 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        if (o.destructive && !armed) drawRoundRect(HUB_COVER_RED.copy(alpha = 0.55f), cornerRadius = CornerRadius(3.dp.toPx()))
                        if (o.destructive) drawRoundRect(HUB_COVER_RED, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        WinFullText(o.label.uppercase(), color = if (o.destructive) HUB_COVER_RED else HUB_ENGRAVE, fontSize = 13.sp, maxLines = 2,
                            fontWeight = FontWeight.Black, letterSpacing = 1.sp, lineHeight = 17.sp)
                        o.sub?.let { WinFullText(it, color = HUB_ENGRAVE.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                        if (armed) Text("Cover lifted - press A again to confirm, move away to cancel", color = HUB_COVER_RED, fontSize = 11.sp,
                            fontWeight = FontWeight.Black, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp)
                                .border(1.dp, HUB_COVER_RED, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
        }
        if (ctx.options.isEmpty()) Text("No switches on this panel.", color = HUB_ENGRAVE.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
