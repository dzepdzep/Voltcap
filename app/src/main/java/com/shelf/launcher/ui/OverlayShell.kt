package com.shelf.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The window around one in-game overlay (stats, music, trophies), update 121:
 *  - a small arrow folds it away, shrinking into its corner. Update 122: the arrow sits in the overlay's outer
 *    corner (the one nearest the screen's corner), not under it;
 *  - folded, only a little arrow tab is left in that corner; a tap grows the overlay back out;
 *  - update 122: a drag from one of the overlay's corners resizes it straight away (outwards bigger, inwards
 *    smaller), and so does a pinch with two fingers on it; a drag from anywhere else moves it (the service);
 *  - it scales its contents (Overlay size × the overlay's own size) inside a wrap-content window, so the
 *    window is always exactly as big as what's drawn and touches land where things are.
 * When the overlay itself is hidden (no song playing, no achievement data) the window is 0 × 0.
 */
@SuppressLint("ViewConstructor")
internal class OverlayShell(
    context: Context,
    val content: View,
    /** About to fold: the service closes an open music / achievements panel first. */
    private val beforeFold: () -> Unit,
    /** Folded (true) or back out (false): the service saves it and places the window (edge / corner margin). */
    private val onFoldChanged: (Boolean) -> Unit,
    /** May the overlay be resized right now (not while its big panel is open)? */
    private val canResize: () -> Boolean,
    /** The overlay's own size while resizing (1 = the Overlay size setting alone); [Boolean] = let go. */
    private val onResize: (Float, Boolean) -> Unit
) : FrameLayout(context) {
    private val d = context.resources.displayMetrics.density
    private fun px(dp: Float) = (dp * d).toInt()

    private val scaler = FrameLayout(context).apply {
        pivotX = 0f; pivotY = 0f
        clipChildren = false
    }
    private val holder = FrameLayout(context)
    private val arrow = Chevron(context)
    /**
     * Update 224 (#107): the fold arrow quiets down when untouched (Settings › Overlay › Auto-fade controls). Folded, the
     * tab is the only thing left of the overlay: it fades, but never goes fully invisible.
     */
    private val arrowFade = IdleFade(arrow, canHide = { !folded })

    /** Folded away: only the arrow tab shows. */
    var folded = false
        private set
    /** The overlay sits in the lower half: the arrow goes above it and the folded tab points up. */
    var atBottom = false
        set(v) { if (field != v) { field = v; arrange() } }
    /** The overlay sits on the right: it folds towards that side, its arrow in its right corner. */
    var atEnd = false
        set(v) { if (field != v) { field = v; arrange() } }
    /** The arrow under the overlay (Overlays › Fold arrows). The folded tab always shows. */
    var arrowsOn = true
        set(v) { if (field != v) { field = v; arrange() } }
    /** The overlay's own size, times the Overlay size setting (the service keeps it). */
    var ownSize = 1f
    /**
     * Update 179: a corner drag starts — which corner ([right], [bottom]). The service keeps the opposite corner where it
     * is, so the overlay and what's in it follow the finger (it used to grow from its top left whichever corner you took).
     */
    var onResizeStart: ((right: Boolean, bottom: Boolean) -> Unit)? = null
    /** The scale everything is drawn at (Overlay size × own size). */
    var scale = 1f
        set(v) {
            if (field != v) {
                field = v
                scaler.scaleX = v; scaler.scaleY = v
                requestLayout()
            }
        }

    /**
     * Update 202: fit the screen (the achievements overlay). The content is measured at its natural size (as wide as the
     * screen allows at this scale, as tall as it needs) and drawn at [scale], held down just enough that the whole box
     * fits on the screen. Before, a bigger Size squeezed it into the room the window was first offered (Android
     * measures a wrap-content window at a dialog's width first — about 320 dp — and the frame never said that was too
     * narrow), so its text wrapped into more lines than there was height for and the bottom was cut off.
     */
    var fitScreen = false
        set(v) { if (field != v) { field = v; requestLayout() } }
    /** Update 202: the scale really drawn at (below [scale] when the box had to shrink to fit the screen). */
    private var drawnScale = 1f

    /** Update 202: the own size that gives what's drawn now (a resize starts from what you see, not a clamped wish). */
    private fun ownNow(): Float = if (fitScreen && scale > 0f && drawnScale < scale) ownSize * drawnScale / scale else ownSize

    init {
        clipChildren = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        holder.addView(content, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        scaler.addView(holder, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        // Over the overlay's outer corner (drawn on top of it, so it takes the tap there).
        scaler.addView(arrow)
        addView(scaler, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        arrow.setOnClickListener { setFolded(!folded, animate = true) }
        arrange()
    }

    /** Fold away / bring back. [animate] = the overlay shrinks into its corner / grows back out of it. */
    fun setFolded(f: Boolean, animate: Boolean, tell: Boolean = true) {
        if (f == folded) return
        holder.animate().cancel()
        if (f) {
            beforeFold()
            folded = true
            if (animate && holder.visibility == View.VISIBLE && holder.width > 0 && content.visibility == View.VISIBLE) {
                holder.pivotX = if (atEnd) holder.width.toFloat() else 0f
                holder.pivotY = if (atBottom) holder.height.toFloat() else 0f
                holder.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(180L)
                    .withEndAction { finishFold(tell) }.start()
            } else finishFold(tell)
        } else {
            folded = false
            holder.visibility = View.VISIBLE
            arrange()
            arrowFade.poke()
            if (animate) {
                holder.alpha = 0f; holder.scaleX = 0.2f; holder.scaleY = 0.2f
                // Grows out of the corner once it has its size again.
                androidx.core.view.OneShotPreDrawListener.add(holder) {
                    holder.pivotX = if (atEnd) holder.width.toFloat() else 0f
                    holder.pivotY = if (atBottom) holder.height.toFloat() else 0f
                    holder.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200L).start()
                }
            } else { holder.alpha = 1f; holder.scaleX = 1f; holder.scaleY = 1f }
            if (tell) onFoldChanged(false)
        }
    }

    private fun finishFold(tell: Boolean) {
        if (!folded) return
        holder.visibility = View.GONE
        holder.alpha = 1f; holder.scaleX = 1f; holder.scaleY = 1f
        arrange()
        arrowFade.poke()
        if (tell) onFoldChanged(true)
    }

    /**
     * The arrow in the overlay's outer corner (top or bottom, left or right: the corner nearest the screen's), pointing
     * the way it folds; folded it's a little tab in that same corner, flat against the screen's edge.
     */
    private fun arrange() {
        // Unfolded it points the way it folds (towards the edge); folded it points back in.
        arrow.up = if (folded) atBottom else !atBottom
        arrow.visibility = if (folded || arrowsOn) View.VISIBLE else View.GONE
        val r = 7 * d
        arrow.background = GradientDrawable().apply {
            if (folded) {
                setColor(0xD0000000.toInt())
                // Flat against the screen's edge, round on the inside.
                cornerRadii = if (atBottom) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
                setStroke(px(1f), 0x40FFFFFF)
            } else {
                setColor(0xB0000000.toInt())
                // Square in the overlay's own corner, round on the inside.
                val tl = if (!atBottom && !atEnd) 0f else r
                val tr = if (!atBottom && atEnd) 0f else r
                val br = if (atBottom && atEnd) 0f else r
                val bl = if (atBottom && !atEnd) 0f else r
                cornerRadii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
                setStroke(px(1f), 0x30FFFFFF)
            }
        }
        arrow.layoutParams = FrameLayout.LayoutParams(
            if (folded) px(38f) else px(24f), if (folded) px(20f) else px(16f),
            (if (atBottom) Gravity.BOTTOM else Gravity.TOP) or (if (atEnd) Gravity.END else Gravity.START)
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The overlay itself is hidden: nothing to show, not even the tab.
        if (content.visibility == View.GONE) { setMeasuredDimension(0, 0); return }
        if (fitScreen) { measureFitting(widthMeasureSpec, heightMeasureSpec); return }
        val s = scale.coerceAtLeast(0.1f)
        fun inner(spec: Int) = if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED) spec
        else MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(spec) / s).toInt(), MeasureSpec.AT_MOST)
        scaler.measure(inner(widthMeasureSpec), inner(heightMeasureSpec))
        setMeasuredDimension(ceil(scaler.measuredWidth * s).toInt(), ceil(scaler.measuredHeight * s).toInt())
    }

    /** Update 202: see [fitScreen]. */
    private fun measureFitting(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The whole screen this window is on, less a margin all round (the corner gap).
        val m = android.util.DisplayMetrics()
        val disp = display
        @Suppress("DEPRECATION")
        if (disp != null) disp.getRealMetrics(m) else m.setTo(resources.displayMetrics)
        val roomW = (m.widthPixels - px(16f)).coerceAtLeast(px(80f))
        val roomH = (m.heightPixels - px(16f)).coerceAtLeast(px(60f))
        // Its natural size: every text has its own width and line limit (they end in "…"), the open panel is 320 dp wide.
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        scaler.measure(unspecified, unspecified)
        val nw = scaler.measuredWidth.coerceAtLeast(1)
        val nh = scaler.measuredHeight.coerceAtLeast(1)
        // Drawn at the chosen size, but never bigger than fits on the screen (across and down).
        val s = minOf(scale.coerceIn(0.1f, 5f), roomW / nw.toFloat(), roomH / nh.toFloat()).coerceAtLeast(0.1f)
        if (drawnScale != s || scaler.scaleX != s) { drawnScale = s; scaler.scaleX = s; scaler.scaleY = s }
        val w = ceil(scaler.measuredWidth * s).toInt()
        val h = ceil(scaler.measuredHeight * s).toInt()
        // Narrower room offered than it needs (that first dialog-width try): say so, and Android offers the full width.
        setMeasuredDimension(resolveSizeAndState(w, widthMeasureSpec, 0), resolveSizeAndState(h, heightMeasureSpec, 0))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        scaler.layout(0, 0, scaler.measuredWidth, scaler.measuredHeight)
    }

    // The music / achievements panels close on a touch outside: the window hears it, the panel acts on it.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { content.dispatchTouchEvent(ev); return true }
        // (Update 224: any touch on the overlay brings its arrow back — and the touch still does what it does.)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) arrowFade.poke()
        return super.dispatchTouchEvent(ev)
    }

    /** Update 224: the fade timings changed (settings): start their countdown again with the new values. */
    fun pokeControls() = arrowFade.poke()

    // ---- Resize: drag from a corner, or pinch with two fingers ----
    private val slop = 8 * d
    private var armed = false
    private var resizing = false
    private var pinching = false
    private var downX = 0f
    private var downY = 0f
    private var pressRight = false
    private var pressBottom = false
    private var startOwn = 1f
    private var startW = 1
    private var startH = 1
    private var startDist = 1f

    /**
     * A press in one of the overlay's corners (not on the folded tab, not with a panel open). On a short overlay (the
     * stats chip) its two ends; the middle always stays for moving it.
     */
    private fun inCorner(ev: MotionEvent): Boolean {
        if (folded || !canResize() || width <= 0 || height <= 0) return false
        val zx = px(26f).coerceAtMost(width / 4).toFloat()
        val zy = px(26f).coerceAtMost(height / 2).toFloat()
        val x = ev.x; val y = ev.y
        return (x < zx || x > width - zx) && (y < zy || y > height - zy)
    }

    private fun startResize() {
        onResizeStart?.invoke(pressRight, pressBottom)
        resizing = true
        // Update 202: from the size drawn now (ownNow), so shrinking a box held down to fit the screen answers at once.
        startOwn = ownNow(); startW = width.coerceAtLeast(1); startH = height.coerceAtLeast(1)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scaler.alpha = 0.8f
    }

    private fun spread(ev: MotionEvent): Float {
        if (ev.pointerCount < 2) return 1f
        val dx = ev.getX(0) - ev.getX(1)
        val dy = ev.getY(0) - ev.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    }

    private fun track(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizing = false; pinching = false
                armed = inCorner(ev)
                if (armed) {
                    downX = ev.rawX; downY = ev.rawY
                    pressRight = ev.x > width / 2f; pressBottom = ev.y > height / 2f
                }
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger on the overlay: pinch to resize (whatever the first finger was doing).
                if (ev.pointerCount == 2 && !folded && canResize()) {
                    armed = false; resizing = false
                    pinching = true
                    startOwn = ownNow(); startDist = spread(ev) // Update 202: see startResize
                    scaler.alpha = 0.8f
                    return true
                }
                return pinching || resizing
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching) {
                    if (ev.pointerCount >= 2) onResize((startOwn * spread(ev) / startDist).coerceIn(0.3f, 3f), false)
                    return true
                }
                if (resizing) { resize(ev, false); return true }
                // A drag from a corner resizes at once (it used to need a long press first, and the drag moved it instead).
                if (armed && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                    armed = false
                    startResize()
                    resize(ev, false)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pinching) {
                    pinching = false
                    scaler.alpha = 1f
                    onResize(ownSize, true)
                    return true
                }
                return resizing
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                armed = false
                if (pinching) {
                    pinching = false
                    scaler.alpha = 1f
                    onResize(ownSize, true)
                    return true
                }
                if (resizing) {
                    resizing = false
                    scaler.alpha = 1f
                    if (ev.actionMasked == MotionEvent.ACTION_UP) resize(ev, true) else onResize(ownSize, true)
                    return true
                }
                return false
            }
        }
        return resizing || pinching
    }

    /** Dragging away from the pressed corner grows it; towards the middle shrinks it. */
    private fun resize(ev: MotionEvent, done: Boolean) {
        val sx = if (pressRight) 1f else -1f
        val sy = if (pressBottom) 1f else -1f
        val grow = ((startW + sx * (ev.rawX - downX)) / startW + (startH + sy * (ev.rawY - downY)) / startH) / 2f
        onResize((startOwn * grow).coerceIn(0.3f, 3f), done)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = track(ev)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Reached only when nothing inside took the touch: keep it (a corner press may become a resize, a second
        // finger a pinch). Touches on the overlay never reach the game anyway.
        val isDown = event.actionMasked == MotionEvent.ACTION_DOWN
        val r = track(event)
        return r || (isDown && (armed || (!folded && canResize()))) || resizing || pinching
    }

    override fun onDetachedFromWindow() {
        holder.animate().cancel()
        super.onDetachedFromWindow()
    }

    /** A small chevron, pointing up or down. */
    private class Chevron(context: Context) : View(context) {
        private val dd = context.resources.displayMetrics.density
        var up = true
            set(v) { if (field != v) { field = v; invalidate() } }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f * dd
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()
        init { isClickable = true; contentDescription = "Fold" }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val hw = minOf(width * 0.22f, 6f * dd)
            val hh = minOf(height * 0.2f, 3f * dd)
            path.reset()
            if (up) { path.moveTo(cx - hw, cy + hh); path.lineTo(cx, cy - hh); path.lineTo(cx + hw, cy + hh) }
            else { path.moveTo(cx - hw, cy - hh); path.lineTo(cx, cy + hh); path.lineTo(cx + hw, cy - hh) }
            canvas.drawPath(path, paint)
        }
    }
}
