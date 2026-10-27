package com.paul.urdukb.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.paul.urdukb.R
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The four key rows, drawn to Gboard's measurements (Pixel 8a, 420 dpi):
 * rows 64.8 dp apart, letters 27 dp Google Sans Text, 36 dp tall pills for ?123 / space / Enter,
 * 10 equal keys across the top row.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** [text] is the character to type for letter and symbol keys, already in the right case. */
        fun onKey(key: KeyDef, text: String?)
        fun onCursorMove(delta: Int)
        fun onFeedback(key: KeyDef)
        /** The finger slid across the letters and lifted: [path] is where it went (glide typing). */
        fun onGlide(path: List<GlidePoint>)
    }

    companion object {
        const val ROW_DP = 64.8f
        const val ROWS = 4
        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_CAPS = 2
        const val ENTER_RETURN = 0
        const val ENTER_GO = 1
        const val ENTER_SEARCH = 2
        const val ENTER_SEND = 3
        const val ENTER_DONE = 4
        const val ENTER_NEXT = 5
        private const val LONG_PRESS_MS = 320L
        private const val REPEAT_FIRST_MS = 400L
        private const val REPEAT_MS = 45L
    }

    var listener: Listener? = null
    var popupLayer: PopupLayer? = null
    var colors: KbColors = KbColors.of(context)
        set(v) { field = v; invalidate() }
    var page: Page = Page.LETTERS
        set(v) { if (field != v) { field = v; relayout(); invalidate() } }
    var shift: Int = SHIFT_OFF
        set(v) { if (field != v) { field = v; invalidate() } }
    var enterKind: Int = ENTER_RETURN
        set(v) { if (field != v) { field = v; invalidate() } }
    var showHints = true
        set(v) { field = v; invalidate() }
    var showPreview = true
    var glideEnabled = true

    private class KeyRect(val def: KeyDef, val rect: RectF)

    private val d = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var keys: List<KeyRect> = emptyList()
    private val font: Typeface = ResourcesCompat.getFont(context, R.font.google_sans_text) ?: Typeface.DEFAULT
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons = HashMap<Int, Drawable>()

    private inner class Ptr(val id: Int, var key: KeyRect, val downX: Float, val downY: Float) {
        /** Every position of the finger, while it may still turn into a glide. */
        var trail: ArrayList<GlidePoint>? = null
        var gliding = false
        var items: List<String>? = null
        var dir = 1
        var sel = -1
        var cursorMode = false
        var lastCursorX = downX
        val longPress = Runnable { startPopup(this) }
        val repeat = object : Runnable {
            override fun run() {
                listener?.onKey(key.def, null)
                handler.postDelayed(this, REPEAT_MS)
            }
        }
    }

    private val ptrs = HashMap<Int, Ptr>()

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var fadePath: Path? = null
    private var fadeAlpha = 0f
    private val fader = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 260
        addUpdateListener { fadeAlpha = it.animatedValue as Float; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) { fadePath = null; invalidate() }
        })
    }

    private fun pathOf(points: List<GlidePoint>): Path {
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        return path
    }

    /** Where the letter keys are, for the glide decoder. Null when the letter page is not showing. */
    fun glideLayout(): GlideLayout? {
        if (page != Page.LETTERS || width == 0) return null
        val centers = HashMap<Char, GlidePoint>()
        for (k in keys) if (isLetterKey(k.def)) centers[k.def.label[0].lowercaseChar()] = GlidePoint(k.rect.centerX(), k.rect.centerY())
        return GlideLayout(centers, width / 10f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (ROW_DP * ROWS * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0) return
        val rows = Layouts.rows(page)
        val rowH = height / ROWS.toFloat()
        val out = ArrayList<KeyRect>()
        rows.forEachIndexed { i, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            row.forEach { k ->
                val w = width * k.weight / total
                out += KeyRect(k, RectF(x, i * rowH, x + w, (i + 1) * rowH))
                x += w
            }
        }
        keys = out
    }

    private fun keyAt(x: Float, y: Float): KeyRect? {
        val cy = y.coerceIn(0f, height - 1f)
        val cx = x.coerceIn(0f, width - 1f)
        var best: KeyRect? = null
        for (k in keys) {
            if (k.def.type == KeyType.SPACER) continue
            if (cy >= k.rect.top && cy < k.rect.bottom && cx >= k.rect.left && cx < k.rect.right) return k
            if (cy >= k.rect.top && cy < k.rect.bottom) {
                // a click on a spacer lands on the nearest key
                if (best == null || abs(cx - k.rect.centerX()) < abs(cx - best.rect.centerX())) best = k
            }
        }
        return best
    }

    /** For the emulator screenshots: where each key is on the screen. */
    fun keyCenters(): List<Triple<String, Float, Float>> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return keys.filter { it.def.type != KeyType.SPACER }.map {
            val name = when (it.def.type) {
                KeyType.CHAR, KeyType.SYMBOLS -> it.def.label
                else -> it.def.type.name.lowercase()
            }
            Triple(name, loc[0] + it.rect.centerX(), loc[1] + it.rect.centerY())
        }
    }

    private fun icon(res: Int): Drawable = icons.getOrPut(res) { context.getDrawable(res)!!.mutate() }

    private fun isLetterKey(def: KeyDef) = def.type == KeyType.CHAR && def.label.length == 1 && def.label[0].isLetter()

    private fun cased(s: String): String =
        if (page == Page.LETTERS && shift != SHIFT_OFF) s.uppercase() else s

    // ------------------------------------------------------------------ drawing

    private fun drawIcon(c: Canvas, res: Int, cx: Float, cy: Float, sizeDp: Float, color: Int) {
        val dr = icon(res)
        val s = (sizeDp * d).toInt()
        dr.setBounds((cx - s / 2f).toInt(), (cy - s / 2f).toInt(), (cx + s / 2f).toInt(), (cy + s / 2f).toInt())
        dr.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        dr.draw(c)
    }

    private fun drawText(c: Canvas, s: String, cx: Float, cy: Float, sizeDp: Float, color: Int) {
        textPaint.textSize = sizeDp * d
        textPaint.color = color
        // text sits on a shared baseline so commas and periods fall where Gboard puts them
        c.drawText(s, cx, cy + 0.716f * sizeDp * d / 2f, textPaint)
    }

    private fun drawPill(c: Canvas, r: RectF, insetDp: Float, color: Int, pressed: Boolean, textColor: Int) {
        val h = 36f * d
        val rect = RectF(r.left + insetDp * d, r.centerY() - h / 2f, r.right - insetDp * d, r.centerY() + h / 2f)
        fill.color = if (pressed) ColorUtils.blendARGB(color, textColor, 0.22f) else color
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
    }

    override fun onDraw(c: Canvas) {
        val col = colors
        val down = ptrs.values.filter { !it.gliding }.map { it.key }
        for (k in keys) {
            val def = k.def
            val r = k.rect
            val cx = r.centerX()
            val cy = r.centerY()
            val pressed = down.any { it === k }
            when (def.type) {
                KeyType.SPACER -> {}
                KeyType.CHAR -> {
                    if (pressed && ptrs.values.none { it.items != null && it.key === k }) {
                        fill.color = col.pressed
                        c.drawCircle(cx, cy, 24f * d, fill)
                    }
                    drawText(c, cased(def.label), cx, cy, 27f, col.keyText)
                    if (page == Page.LETTERS && showHints && def.hint != null) {
                        drawText(c, def.hint, r.right - 5.7f * d, r.top + 14.1f * d, 11f, ColorUtils.setAlphaComponent(col.hint, 200))
                    }
                }
                KeyType.SYMBOLS -> {
                    val pill = def.label == "?123" || def.label == "ABC"
                    if (pill) {
                        drawPill(c, r, 4.6f, col.pill, pressed, col.pillText)
                        drawText(c, def.label, cx, cy, 19f, col.pillText)
                    } else {
                        if (pressed) { fill.color = col.pressed; c.drawCircle(cx, cy, 24f * d, fill) }
                        drawText(c, def.label, cx, cy, 19f, col.keyText)
                    }
                }
                KeyType.SPACE -> drawPill(c, r, 0.5f, col.space, pressed, col.keyText)
                KeyType.ENTER -> {
                    val plain = enterKind == ENTER_RETURN
                    val bg = if (plain) col.pill else col.action
                    val fg = if (plain) col.pillText else col.actionText
                    drawPill(c, r, 4.6f, bg, pressed, fg)
                    val res = when (enterKind) {
                        ENTER_GO, ENTER_NEXT -> R.drawable.kb_go
                        ENTER_SEARCH -> R.drawable.kb_search
                        ENTER_SEND -> R.drawable.kb_send
                        ENTER_DONE -> R.drawable.kb_done
                        else -> R.drawable.kb_enter
                    }
                    drawIcon(c, res, cx, cy, 30f, fg)
                }
                KeyType.SHIFT -> {
                    if (pressed) { fill.color = col.pressed; c.drawCircle(cx, cy, 24f * d, fill) }
                    val res = when (shift) { SHIFT_ON -> R.drawable.kb_shift_on; SHIFT_CAPS -> R.drawable.kb_caps; else -> R.drawable.kb_shift }
                    drawIcon(c, res, cx, cy, 28f, col.keyText)
                }
                KeyType.DELETE -> {
                    if (pressed) { fill.color = col.pressed; c.drawCircle(cx, cy, 24f * d, fill) }
                    drawIcon(c, R.drawable.kb_delete, cx, cy, 31f, col.keyText)
                }
                KeyType.EMOJI -> {
                    if (pressed) { fill.color = col.pressed; c.drawCircle(cx, cy, 24f * d, fill) }
                    drawIcon(c, R.drawable.kb_emoji, cx, cy, 24f, col.keyText)
                }
            }
        }
        // the glide trail
        trailPaint.strokeWidth = 7f * d
        for (p in ptrs.values) {
            val t = p.trail
            if (p.gliding && t != null && t.size > 1) {
                trailPaint.color = ColorUtils.setAlphaComponent(col.selected, 190)
                c.drawPath(pathOf(t), trailPaint)
            }
        }
        fadePath?.let {
            trailPaint.color = ColorUtils.setAlphaComponent(col.selected, (190 * fadeAlpha).toInt())
            c.drawPath(it, trailPaint)
        }
    }

    // ------------------------------------------------------------------ touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (com.paul.urdukb.BuildConfig.PREVIEW) android.util.Log.i("PREVIEW", "KVTOUCH ${ev.actionMasked} ${ev.x.toInt()} ${ev.y.toInt()}")
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                down(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until ev.pointerCount) move(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = ev.actionIndex
                up(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        val k = keyAt(x, y) ?: return
        val p = Ptr(id, k, x, y)
        if (glideEnabled && page == Page.LETTERS && isLetterKey(k.def)) p.trail = arrayListOf(GlidePoint(x, y))
        ptrs[id]?.let { release(it) }
        ptrs[id] = p
        listener?.onFeedback(k.def)
        when {
            k.def.type == KeyType.DELETE -> {
                listener?.onKey(k.def, null)
                handler.postDelayed(p.repeat, REPEAT_FIRST_MS)
            }
            k.def.popups.isNotEmpty() -> handler.postDelayed(p.longPress, LONG_PRESS_MS)
        }
        showPreviewFor(k)
        invalidate()
    }

    private fun showPreviewFor(k: KeyRect) {
        if (!showPreview) return
        val def = k.def
        if (def.type == KeyType.CHAR && def.label.isNotBlank() && def.label != "," && def.label != ".") {
            popupLayer?.showPreview(this, k.rect, cased(def.label))
        } else popupLayer?.hide()
    }

    private fun startPopup(p: Ptr) {
        val k = p.key
        val list = k.def.popups.map { cased(it) }
        if (list.isEmpty()) return
        p.items = list
        p.dir = if (k.rect.centerX() < width / 2f) 1 else -1
        p.sel = 0
        popupLayer?.showPopup(this, k.rect, list, p.dir, 0)
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = ptrs[id] ?: return
        if (p.items != null) {
            val i = popupLayer?.indexAt(this, x, y) ?: -1
            if (i >= 0) { p.sel = i; popupLayer?.setSelected(i) }
            return
        }
        val trail = p.trail
        if (trail != null) {
            trail.add(GlidePoint(x, y))
            if (!p.gliding && hypot(x - p.downX, y - p.downY) > 0.8f * (width / 10f)) {
                // moved far enough from the key it started on: this is a glide, not a tap
                p.gliding = true
                handler.removeCallbacks(p.longPress)
                popupLayer?.hide()
            }
            if (p.gliding) { invalidate(); return }
        }
        val def = p.key.def
        if (def.type == KeyType.SPACE) {
            if (!p.cursorMode && abs(x - p.downX) > 14f * d) { p.cursorMode = true; p.lastCursorX = x }
            if (p.cursorMode) {
                val step = 14f * d
                while (abs(x - p.lastCursorX) >= step) {
                    val dir = if (x > p.lastCursorX) 1 else -1
                    p.lastCursorX += dir * step
                    listener?.onCursorMove(dir)
                }
                return
            }
        }
        val nk = keyAt(x, y) ?: return
        if (nk !== p.key && def.type != KeyType.DELETE) {
            handler.removeCallbacks(p.longPress)
            p.key = nk
            if (nk.def.popups.isNotEmpty()) handler.postDelayed(p.longPress, LONG_PRESS_MS)
            showPreviewFor(nk)
            invalidate()
        }
    }

    private fun up(id: Int, x: Float, y: Float) {
        val p = ptrs.remove(id) ?: return
        release(p)
        val trail = p.trail
        if (p.gliding && trail != null) {
            trail.add(GlidePoint(x, y))
            var len = 0f
            for (i in 1 until trail.size) len += hypot(trail[i].x - trail[i - 1].x, trail[i].y - trail[i - 1].y)
            if (len >= 1.4f * (width / 10f)) {
                fadePath = pathOf(trail)
                fadeAlpha = 1f
                fader.cancel(); fader.start()
                if (ptrs.isEmpty()) popupLayer?.hide()
                listener?.onGlide(trail)
                invalidate()
                return
            }
            // a short slide: just type the key the finger ended on
            keyAt(x, y)?.let { p.key = it }
        }
        val def = p.key.def
        val items = p.items
        when {
            items != null -> if (p.sel in items.indices) listener?.onKey(def, items[p.sel])
            p.cursorMode -> {}
            def.type == KeyType.DELETE -> {}
            def.type == KeyType.CHAR -> listener?.onKey(def, cased(def.label))
            else -> listener?.onKey(def, null)
        }
        if (ptrs.isEmpty()) popupLayer?.hide()
        invalidate()
    }

    private fun release(p: Ptr) {
        handler.removeCallbacks(p.longPress)
        handler.removeCallbacks(p.repeat)
    }

    private fun cancelAll() {
        ptrs.values.forEach { release(it) }
        ptrs.clear()
        popupLayer?.hide()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
    }
}
