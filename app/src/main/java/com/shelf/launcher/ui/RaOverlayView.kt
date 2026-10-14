package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import com.shelf.launcher.data.LightsTheme
import com.shelf.launcher.data.RaOverlayData
import com.shelf.launcher.data.perf.PerfConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The RetroAchievements overlay over a game. Collapsed: the compact summary — update 202: by default only the badge
 * of the pinned achievement (else the next one to go for) and a progress capsule; its name, description, totals, last
 * unlock, missables and next-one lines are each switched on separately (see bindSummaryLines). Tap it to
 * open a small panel: how far you are into the set on top, then what's left, only the missables or what
 * you've unlocked (tabs), each achievement with its badge, points and how many players have it, and a tap
 * on one loads its comments from RetroAchievements underneath it, scrollable. Tap outside or ✕:
 * collapses. Touch only - the window never takes focus, so the controller stays with the game.
 *
 * Every part can be switched off (Settings › Performance overlay › Achievements over games). The Voltcap
 * look uses the home screen's accent colour; off, it's the plain dark box.
 */
internal class RaOverlayView(
    context: Context,
    private val scope: CoroutineScope,
    private val onExpandedChange: (Boolean) -> Unit
) : FrameLayout(context) {
    private val d = context.resources.displayMetrics.density
    private fun px(dp: Float) = (dp * d).toInt()
    var expanded = false
        private set
    private var state: RaOverlayData.State? = null
    private var cfg: PerfConfig? = null
    private var tab = 0 // 0 = left, 1 = missables, 2 = unlocked
    /** Rows with their comments open: "pin:<id>" for the pinned row, "<id>" in the list (update 91: they shared one
     *  id, so after opening the pinned one's comments the same achievement's list row needed two taps). */
    private val openComments = mutableSetOf<String>()

    private val accent get() = LightsTheme.accent or 0xFF000000.toInt()
    private val gold = 0xFFFFC94D.toInt()

    // Collapsed: [badge] [bar / text]
    private val summaryBox = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = px(8f)
        setPadding(pad, pad / 2, pad, pad / 2)
    }
    private val summaryBadge = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val summaryBar = CapsuleBar(context)
    /**
     * Update 202: the summary's parts are their own lines (they were one text, so a long description or missables list
     * just ran on and the box got cut off at the bottom). Each has a line limit and ends in "…"; each is switched on its
     * own (Settings › Overlay › Achievements over games, and the in-game overlay panel).
     */
    private fun summaryLine(sp: Float, color: Int, lines: Int, bold: Boolean = false) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxWidth = px(260f)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    private val sumName = summaryLine(11f, Color.WHITE, 2, bold = true)
    private val sumDesc = summaryLine(10f, 0xCCFFFFFF.toInt(), 3)
    private val sumTotals = summaryLine(11f, 0xFFFFC94D.toInt(), 1, bold = true)
    private val sumLast = summaryLine(10f, 0xFF5BD68A.toInt(), 1)
    private val sumMissables = summaryLine(10f, 0xFFFFB547.toInt(), 2)
    private val sumNext = summaryLine(11f, Color.WHITE, 2, bold = true)
    private val sumWhy = summaryLine(10f, 0x99FFFFFF.toInt(), 1)
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        val pad = px(10f)
        setPadding(pad, pad, pad, pad)
    }
    private val header = TextView(context).apply {
        setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTypeface(typeface, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    private val headerBar = CapsuleBar(context)
    private val headerStats = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) }
    private val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    /** The pinned achievement (the one you're working on), on top of the open panel. */
    private val pinnedBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { addView(list); isVerticalScrollBarEnabled = true }

    init {
        // The summary goes first: the service puts its drag handler on child 0.
        summaryBox.addView(summaryBadge, LinearLayout.LayoutParams(px(34f), px(34f)).apply { marginEnd = px(8f) })
        summaryBox.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(summaryBar, LinearLayout.LayoutParams(px(150f), px(6f)).apply { bottomMargin = px(2f); topMargin = px(2f) })
            // Update 202: one view per part, in the order they read (see summaryLine).
            listOf(sumName, sumDesc, sumTotals, sumLast, sumMissables, sumNext, sumWhy).forEach { v ->
                addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(1f) })
            }
        })
        addView(summaryBox)
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(header, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "✕"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(px(10f), 0, px(4f), 0)
                setOnClickListener { collapse() }
            })
        }
        // Update 202: the open panel is as wide as its list (320 dp), whatever room the window offers — the bar and the
        // pinned row used to stretch to whatever width they were given (the window now measures at the screen's width).
        panel.addView(top, LinearLayout.LayoutParams(px(320f), LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(headerBar, LinearLayout.LayoutParams(px(320f), px(8f)).apply { topMargin = px(6f) })
        panel.addView(headerStats, LinearLayout.LayoutParams(px(320f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(4f) })
        panel.addView(pinnedBox, LinearLayout.LayoutParams(px(320f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
        panel.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
        panel.addView(scroll, LinearLayout.LayoutParams(px(320f), (context.resources.displayMetrics.heightPixels * 0.5f).toInt()).apply { topMargin = px(6f) })
        addView(panel)
        summaryBox.setOnClickListener { if (!moved) expand() }
    }

    /** Overlay opacity at 100 %: the box is fully opaque (the window's own alpha alone left it see-through). Set by the service. */
    var solid = false
        set(v) { if (field != v) { field = v; cfg?.let { applyLook(it) } } }

    private fun applyLook(c: PerfConfig) {
        val a = if (solid) 0xFF000000.toInt() else 0xE6000000.toInt()
        background = if (c.raThemed) GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(blend(accent, 0xFF0A0B12.toInt(), 0.78f) and 0x00FFFFFF or a, (0x0A0B12) or a)
        ).apply { cornerRadius = 14 * d; setStroke(px(1f), (accent and 0x00FFFFFF) or 0x8C000000.toInt()) }
        else GradientDrawable().apply { setColor(if (solid) 0xFF000000.toInt() else 0xC8000000.toInt()); cornerRadius = 10 * d }
        summaryBar.colors(if (c.raThemed) accent else gold, gold)
        headerBar.colors(if (c.raThemed) accent else gold, gold)
    }

    fun bind(st: RaOverlayData.State, c: PerfConfig) {
        state = st; cfg = c
        applyLook(c)
        if (!c.raUnlockedTab && tab == 2) tab = 0
        val pin = pinnedOf(st)
        bindSummaryLines(st, c, pin)
        val p = if (st.total > 0) st.unlocked / st.total.toFloat() else 0f
        summaryBar.progress = p
        summaryBar.visibility = if (c.raProgressBar) View.VISIBLE else View.GONE
        // The badge: the pinned one, else (with Last unlock on) the last unlock for a while, otherwise the next one to go
        // for. Update 202: the next one's badge no longer needs the "Next achievement line" (it's the featured badge now).
        val last = if (c.raLastUnlock) st.lastUnlockBadge else null
        val badge = pin?.badge ?: last ?: st.nextBadge
        // Only an unlocked one is shown in colour: the pinned / next one is still to get, so grey like RetroAchievements' locked badges.
        val stillLocked = badge != null && last != badge
        if (c.raBadges && badge != null) {
            summaryBadge.visibility = View.VISIBLE
            if (stillLocked) { summaryBadge.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }); summaryBadge.imageAlpha = 150 }
            else { summaryBadge.colorFilter = null; summaryBadge.imageAlpha = 255 }
            summaryBadge.load(badge) { crossfade(true); size(px(34f)) }
            summaryBadge.background = GradientDrawable().apply { cornerRadius = 7 * d; setColor(0x33FFFFFF) }
            summaryBadge.clipToOutline = true
            summaryBadge.outlineProvider = roundOutline(7f)
        } else summaryBadge.visibility = View.GONE
        // Update 202: everything switched off (or no badge to show): the totals line, so there's still a box to tap.
        if (summaryBadge.visibility != View.VISIBLE && summaryBar.visibility != View.VISIBLE &&
            listOf(sumName, sumDesc, sumTotals, sumLast, sumMissables, sumNext, sumWhy).none { it.visibility == View.VISIBLE }
        ) show(sumTotals, totalsText(st, c))
        if (expanded) rebuild()
    }

    /** Update 202: [v] shows [text], or is gone when there's nothing to show. */
    private fun show(v: TextView, text: CharSequence?) {
        if (text.isNullOrBlank()) { v.visibility = View.GONE; v.text = "" } else { v.text = text; v.visibility = View.VISIBLE }
    }

    /** Update 202: "🏆 37/75 · 49 %" (+ " · 227/616 pts" with Points on). */
    private fun totalsText(st: RaOverlayData.State, c: PerfConfig): String {
        val pct = if (st.total > 0) st.unlocked * 100 / st.total else 0
        return "🏆 ${st.unlocked}/${st.total} · $pct %" + (if (c.raPoints) " · ${st.points}/${st.totalPoints} pts" else "")
    }

    /**
     * Update 202: the small overlay's lines, each one only when switched on. The featured achievement is the pinned one
     * (📌), else the next one worth going for (RaOverlayData: a missable you can still get, then the easiest left, then the
     * set's order) — the badge shows it; Name and Description add its name and what to do. Nothing is said twice: with the
     * next one already named, the Next line only says why it's the one, and the missables line leaves out names shown above.
     */
    private fun bindSummaryLines(st: RaOverlayData.State, c: PerfConfig, pin: RaOverlayData.Item?) {
        val mastered = st.total > 0 && st.unlocked == st.total
        val title = pin?.title ?: st.nextTitle
        val missable = if (pin != null) pin.missable else st.nextIsMissable
        val shown = mutableSetOf<String>()
        // Name
        if (c.raName && title != null) {
            show(sumName, if (pin != null) "📌 $title" else title)
            sumName.setTextColor(if (missable) 0xFFFFB547.toInt() else Color.WHITE)
            shown += title
        } else if ((c.raName || c.raTotals) && mastered) {
            show(sumName, "Mastered - every achievement unlocked")
            sumName.setTextColor(gold)
        } else show(sumName, null)
        // Description (of the same one)
        show(sumDesc, if (c.raDescription && title != null) (if (pin != null) pin.description else st.nextDescription) else null)
        // Totals: count · % · points
        show(sumTotals, if (c.raTotals) totalsText(st, c) else null)
        // Last unlock (for a while)
        show(sumLast, if (c.raLastUnlock) st.lastUnlock?.let { "✓ $it" } else null)
        // Next achievement line: "Next: …" and why that one. When it's already the named featured one, only the why.
        val nextNamed = pin == null && c.raName
        val nextTitle = st.nextTitle
        if (c.raNext && nextTitle != null) {
            if (nextNamed) show(sumNext, null)
            else {
                show(sumNext, "Next: $nextTitle")
                sumNext.setTextColor(if (st.nextIsMissable) 0xFFFFB547.toInt() else Color.WHITE)
                shown += nextTitle
            }
            show(sumWhy, st.nextWhy)
        } else { show(sumNext, null); show(sumWhy, null) }
        // Missables still to get: the count, and a few names not already shown above.
        show(sumMissables, if (c.raMissables && st.missablesLeft.isNotEmpty()) {
            val n = st.missablesLeft.size
            val others = st.missablesLeft.filter { it !in shown }
            val names = others.take(2).joinToString(", ") + if (others.size > 2) " +${others.size - 2}" else ""
            "⚠ $n missable${if (n == 1) "" else "s"} still to get" + if (names.isNotEmpty()) ": $names" else ""
        } else null)
    }

    /**
     * Update 202: which side the window frame's fold arrow sits on (-1 none, 0 start, 1 end), set by the service. The
     * small box keeps that side clear, so the arrow doesn't sit on the badge now that the box is only a badge high.
     */
    var arrowSide = -1
        set(v) {
            if (field == v) return
            field = v
            val pad = px(8f)
            val room = px(28f)
            summaryBox.setPaddingRelative(if (v == 0) room else pad, pad / 2, if (v == 1) room else pad, pad / 2)
        }

    fun expand() {
        if (expanded) return
        expanded = true
        summaryBox.visibility = View.GONE
        panel.visibility = View.VISIBLE
        rebuild()
        onExpandedChange(true)
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        panel.visibility = View.GONE
        summaryBox.visibility = View.VISIBLE
        onExpandedChange(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A touch outside the open panel (the window watches for those) closes it.
        if (event.action == MotionEvent.ACTION_OUTSIDE) { collapse(); return true }
        return super.onTouchEvent(event)
    }

    /** Set by the service: the summary was dragged (not tapped), so don't open on release. */
    var moved = false

    private fun rebuild() {
        val st = state ?: return
        val c = cfg ?: return
        // Rows are made anew (closed): forget which ones had comments open, so one tap opens them again.
        openComments.clear()
        header.text = st.gameTitle
        // How far into the set, on top.
        val p = if (st.total > 0) st.unlocked / st.total.toFloat() else 0f
        headerBar.progress = p
        headerBar.visibility = if (c.raProgressBar) View.VISIBLE else View.GONE
        val pct = (p * 100).toInt()
        headerStats.text = android.text.SpannableStringBuilder().apply {
            val s0 = length
            append("${st.unlocked} / ${st.total}  ·  $pct %")
            setSpan(android.text.style.StyleSpan(Typeface.BOLD), s0, length, 0)
            setSpan(android.text.style.ForegroundColorSpan(gold), s0, length, 0)
            if (c.raPoints) {
                val s1 = length
                append("   ${st.points} / ${st.totalPoints} pts")
                setSpan(android.text.style.ForegroundColorSpan(0xCCFFFFFF.toInt()), s1, length, 0)
            }
            val left = st.total - st.unlocked
            if (left > 0) {
                val s2 = length
                append("   ·  $left to go")
                setSpan(android.text.style.ForegroundColorSpan(0x99FFFFFF.toInt()), s2, length, 0)
            }
        }
        pinnedBox.removeAllViews()
        val pin = pinnedOf(st)
        if (pin != null) {
            pinnedBox.visibility = View.VISIBLE
            pinnedBox.addView(TextView(context).apply {
                text = "📌 PINNED — WORKING ON"
                setTextColor(if (c.raThemed) accent else gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f); setTypeface(typeface, Typeface.BOLD)
            })
            pinnedBox.addView(row(pin, unlocked = pin.id in st.unlockedIds, c, pinnedRow = true))
        } else pinnedBox.visibility = View.GONE
        tabs.removeAllViews()
        val missables = st.left.filter { it.missable }
        val labels = buildList {
            add(0 to "Left ${st.left.size}")
            if (c.raMissables || missables.isNotEmpty()) add(1 to "Missable ${missables.size}")
            if (c.raUnlockedTab) add(2 to "Unlocked ${st.unlockedList.size}")
        }
        labels.forEach { (i, label) ->
            val on = tab == i
            tabs.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(if (on) Color.BLACK else Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(px(10f), px(4f), px(10f), px(4f))
                background = GradientDrawable().apply {
                    setColor(if (on) (if (c.raThemed) accent else gold) else 0x33FFFFFF); cornerRadius = 50 * d
                }
                setOnClickListener { tab = i; rebuild() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(6f) })
        }
        list.removeAllViews()
        val items = when (tab) { 1 -> missables; 2 -> st.unlockedList; else -> st.left }
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = when (tab) { 1 -> "No missables left to get."; 2 -> "Nothing unlocked yet - the first one is waiting."; else -> "Nothing left - mastered!" }
                setTextColor(0xCCFFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
        }
        items.forEach { item -> list.addView(row(item, unlocked = tab == 2, c)) }
    }

    private fun row(item: RaOverlayData.Item, unlocked: Boolean, c: PerfConfig, pinnedRow: Boolean = false): View {
        val openKey = if (pinnedRow) "pin:${item.id}" else item.id.toString()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6f), px(5f), px(6f), px(5f))
            background = GradientDrawable().apply {
                setColor(if (unlocked && c.raThemed) (accent and 0x00FFFFFF) or 0x22000000 else 0x18FFFFFF); cornerRadius = 8 * d
            }
        }
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (c.raBadges && item.badge != null) {
            line.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = roundOutline(6f)
                // Still to get: greyed, like RetroAchievements' own locked badges.
                if (!unlocked) { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }); imageAlpha = 150 }
                load(item.badge) { crossfade(true); size(px(32f)) }
            }, LinearLayout.LayoutParams(px(32f), px(32f)).apply { marginEnd = px(8f) })
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = (if (item.missable && !unlocked) "⚠ " else "") + item.title + (if (c.raPoints) "  · ${item.points} pts" else "") +
                (item.percent?.let { "  · %.0f%%".format(it) } ?: "")
            setTextColor(if (item.missable && !unlocked) 0xFFFFB74D.toInt() else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTypeface(typeface, Typeface.BOLD)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        item.description?.let {
            texts.addView(TextView(context).apply {
                text = it; setTextColor(0xCCFFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); maxLines = 3
            })
        }
        if (unlocked) item.earned?.let { e ->
            texts.addView(TextView(context).apply {
                text = "✓ " + e.take(16); setTextColor(0xFF5BD68A.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            })
        }
        line.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // Pin / unpin while playing: the one you're working on stays on top (and in the small summary).
        val gid = state?.gameId
        if (!unlocked && gid != null) {
            val pinned = com.shelf.launcher.data.RaPin.isPinned(gid, item.id)
            line.addView(TextView(context).apply {
                text = if (pinned) "📌" else "📍"
                alpha = if (pinned) 1f else 0.55f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(px(10f), px(4f), px(4f), px(4f))
                contentDescription = if (pinned) "Unpin" else "Pin"
                setOnClickListener { com.shelf.launcher.data.RaPin.toggle(gid, item.id); rebuild(); state?.let { s -> cfg?.let { c2 -> bind(s, c2) } } }
            })
        }
        box.addView(line)
        val comments = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(comments)
        box.setOnClickListener {
            if (openKey in openComments) { openComments -= openKey; comments.removeAllViews() }
            else { openComments += openKey; loadComments(item.id, comments) }
        }
        return box.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(4f) }
        }
    }

    /** The pinned achievement of this game, when pinned and shown (Overlay settings). */
    private fun pinnedOf(st: RaOverlayData.State): RaOverlayData.Item? {
        if (!com.shelf.launcher.data.Features.raShowPinned.value) return null
        val gid = st.gameId ?: return null
        val id = com.shelf.launcher.data.RaPin.pinnedFor(gid) ?: return null
        return st.all.firstOrNull { it.id == id }
    }

    private fun roundOutline(r: Float) = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r * d)
        }
    }

    private fun loadComments(id: Int, into: LinearLayout) {
        into.removeAllViews()
        val loading = TextView(context).apply {
            text = "Loading comments…"; setTextColor(0x99FFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }
        into.addView(loading)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val creds = com.shelf.launcher.data.RaCredentialsStore(context).load() ?: return@runCatching null
                    com.shelf.launcher.data.RetroAchievementsRepository(com.shelf.launcher.data.createRetroAchievementsApi())
                        .fetchAchievementComments(creds.apiKey, id)
                }.getOrNull()
            }
            into.removeAllViews()
            val page = (result as? com.shelf.launcher.data.ra.RaResult.Ok)?.value
            val list = page?.comments.orEmpty()
            if (list.isEmpty()) {
                into.addView(TextView(context).apply {
                    // Update 144: a failed request says why (no connection, rejected key…), not "No comments yet."
                    text = when (result) {
                        null -> "Sign in to RetroAchievements to read comments."
                        is com.shelf.launcher.data.ra.RaResult.Error -> result.error.message
                        else -> "No comments yet."
                    }
                    setTextColor(0x99FFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                })
            }
            list.take(12).forEach { c ->
                into.addView(TextView(context).apply {
                    text = "${c.User ?: "?"}: ${c.CommentText?.trim()}"
                    setTextColor(0xDDFFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setPadding(px(6f), px(3f), 0, px(3f))
                })
            }
        }
    }
}

private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((((a shr s) and 0xFF) * (1 - t)) + (((b shr s) and 0xFF) * t)).toInt() and 0xFF
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Voltcap's progress capsule: a rounded track with an accent-to-gold charge and a bright tip. Drawn only on change. */
internal class CapsuleBar(context: Context) : View(context) {
    var progress = 0f
        set(v) { val c = v.coerceIn(0f, 1f); if (c != field) { field = c; invalidate() } }
    private var from = 0xFFFF6A00.toInt()
    private var to = 0xFFFFC94D.toInt()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val r = RectF()

    fun colors(a: Int, b: Int) { if (a != from || b != to) { from = a; to = b; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        r.set(0f, 0f, w, h)
        canvas.drawRoundRect(r, h / 2, h / 2, track)
        if (progress <= 0f) return
        val fw = maxOf(h, w * progress)
        fill.shader = LinearGradient(0f, 0f, w, 0f, from, to, Shader.TileMode.CLAMP)
        r.set(0f, 0f, fw, h)
        canvas.drawRoundRect(r, h / 2, h / 2, fill)
        if (progress < 1f) {
            tip.alpha = 200
            canvas.drawCircle(fw - h / 2, h / 2, h * 0.32f, tip)
        }
    }
}
