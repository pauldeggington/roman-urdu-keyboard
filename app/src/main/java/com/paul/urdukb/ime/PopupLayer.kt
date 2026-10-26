package com.paul.urdukb.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.paul.urdukb.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws Gboard's circular key preview and the long-press popup (a pill of circles) over the whole
 * keyboard, including the empty strip above it. It never takes touches; the key view keeps the finger.
 */
class PopupLayer(context: Context) : View(context) {

    var colors: KbColors? = null

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.google_sans_text) ?: Typeface.DEFAULT
    }

    // preview
    private var previewLabel: String? = null
    private var previewCx = 0f
    private var previewBottom = 0f

    // popup
    private var items: List<String>? = null
    private var dir = 1
    private var cols = 1
    private var sel = -1
    private val box = RectF()
    private val cell = 44f * d
    private val rowH = 50f * d
    private var anchorCx = 0f

    override fun onTouchEvent(event: MotionEvent?) = false

    // Must not make the keyboard window taller: take the parent's height only when it is fixed.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(w, h)
    }

    private fun origin(kb: View): FloatArray {
        val a = IntArray(2); val b = IntArray(2)
        kb.getLocationInWindow(a); getLocationInWindow(b)
        return floatArrayOf((a[0] - b[0]).toFloat(), (a[1] - b[1]).toFloat())
    }

    fun showPreview(kb: View, key: RectF, label: String) {
        val o = origin(kb)
        previewLabel = label
        previewCx = o[0] + key.centerX()
        previewBottom = o[1] + key.top - 4f * d
        items = null
        invalidate()
    }

    fun showPopup(kb: View, key: RectF, list: List<String>, direction: Int, selected: Int) {
        val o = origin(kb)
        previewLabel = null
        items = list
        dir = direction
        sel = selected
        cols = if (list.size <= 7) list.size else (list.size + 1) / 2
        val rows = ceil(list.size / cols.toFloat()).toInt()
        anchorCx = o[0] + key.centerX()
        val w = cols * cell
        val h = rows * rowH
        var left = if (dir > 0) anchorCx - cell / 2f else anchorCx + cell / 2f - w
        left = min(max(left, 4f * d), width - w - 4f * d)
        val bottom = o[1] + key.top - 6f * d
        box.set(left, bottom - h, left + w, bottom)
        invalidate()
    }

    /** Index of the popup cell under the finger (window-relative to this layer), or -1. */
    fun indexAt(kb: View, x: Float, y: Float): Int {
        val list = items ?: return -1
        val o = origin(kb)
        val px = o[0] + x
        val py = o[1] + y
        val rows = ceil(list.size / cols.toFloat()).toInt()
        // column counted from the key outward (dir): the first item sits over the key
        val firstCx = if (dir > 0) box.left + cell / 2f else box.right - cell / 2f
        var col = Math.round((px - firstCx) / cell * dir)
        col = col.coerceIn(0, cols - 1)
        var row = 0
        if (rows > 1) {
            // row 0 is the bottom row (nearest the key)
            row = ((box.bottom - py) / rowH).toInt().coerceIn(0, rows - 1)
            if (py > box.bottom) row = 0
        }
        val i = row * cols + col
        return if (i < list.size) i else list.size - 1
    }

    fun setSelected(i: Int) {
        if (i != sel) { sel = i; invalidate() }
    }

    fun hide() {
        if (previewLabel == null && items == null) return
        previewLabel = null
        items = null
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val col = colors ?: return
        previewLabel?.let { label ->
            val r = 27f * d
            val cy = previewBottom - r
            paint.color = col.popupBg
            c.drawCircle(previewCx.coerceIn(r + 2f * d, width - r - 2f * d), cy, r, paint)
            text.color = col.popupText
            text.textSize = 32f * d
            c.drawText(label, previewCx.coerceIn(r + 2f * d, width - r - 2f * d), cy + 0.716f * 32f * d / 2f, text)
        }
        val list = items ?: return
        paint.color = col.popupBg
        val corner = if (box.height() <= rowH + 1f) box.height() / 2f else 26f * d
        c.drawRoundRect(box, corner, corner, paint)
        val rows = ceil(list.size / cols.toFloat()).toInt()
        for (i in list.indices) {
            val row = i / cols
            val colI = i % cols
            val cx = if (dir > 0) box.left + cell / 2f + colI * cell else box.right - cell / 2f - colI * cell
            val cy = box.bottom - rowH / 2f - row * rowH
            if (i == sel) {
                paint.color = col.selected
                c.drawCircle(cx, cy, 20f * d, paint)
                text.color = col.selectedText
            } else text.color = col.popupText
            text.textSize = 22f * d
            c.drawText(list[i], cx, cy + 0.716f * 22f * d / 2f, text)
        }
        if (rows < 0) return
    }
}
