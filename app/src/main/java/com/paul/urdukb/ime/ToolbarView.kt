package com.paul.urdukb.ime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.paul.urdukb.R

/**
 * The strip above the keys. Gboard puts a grid button on the left and a mic circle on the right.
 * Between them: the Translate key, the Casual/Formal word and the Auto/EN→RU word. The grid button swaps
 * those for the tools: emoji, select all, copy, cut, clipboard and settings.
 */
class ToolbarView(context: Context) : View(context) {

    enum class Tool { GRID, TRANSLATE, TONE, DIRECTION, MIC, EMOJI, SELECT_ALL, COPY, CUT, CLIPBOARD, SETTINGS }

    interface Listener {
        fun onTool(tool: Tool)
    }

    companion object {
        const val HEIGHT_DP = 46f
    }

    var listener: Listener? = null
    var colors: KbColors = KbColors.of(context)
        set(v) { field = v; invalidate() }
    var toolsOpen = false
        set(v) { field = v; invalidate() }
    var canRevert = false
        set(v) { field = v; invalidate() }
    var toneLabel = "Casual"
        set(v) { field = v; invalidate() }
    var directionLabel = "Auto"
        set(v) { field = v; invalidate() }
    var busy = false
        set(v) {
            if (field == v) return
            field = v
            if (v) spinner.start() else { spinner.cancel(); angle = 0f }
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.google_sans_text) ?: Typeface.DEFAULT
        textSize = 14f * d
    }
    private val icons = HashMap<Int, Drawable>()
    private var angle = 0f
    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }

    private var pressed: Tool? = null

    private val toolIcons = listOf(
        Tool.EMOJI to R.drawable.kb_emoji,
        Tool.SELECT_ALL to R.drawable.kb_select_all,
        Tool.COPY to R.drawable.kb_copy,
        Tool.CUT to R.drawable.kb_cut,
        Tool.CLIPBOARD to R.drawable.kb_paste,
        Tool.SETTINGS to R.drawable.kb_settings,
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HEIGHT_DP * d).toInt())
    }

    private fun icon(res: Int): Drawable = icons.getOrPut(res) { context.getDrawable(res)!!.mutate() }

    private fun layoutItems(): List<Pair<Tool, RectF>> {
        val h = height.toFloat()
        val out = ArrayList<Pair<Tool, RectF>>()
        out += Tool.GRID to RectF(6f * d, 0f, 6f * d + h, h)
        val micCx = width - 23.5f * d
        val micRect = RectF(micCx - h / 2f, 0f, micCx + h / 2f, h)
        out += Tool.MIC to micRect
        if (toolsOpen) {
            val left = 6f * d + h
            val right = micRect.left
            val slot = (right - left) / toolIcons.size
            toolIcons.forEachIndexed { i, (tool, _) ->
                out += tool to RectF(left + i * slot, 0f, left + (i + 1) * slot, h)
            }
        } else {
            var right = micRect.left
            val dirW = maxOf(52f * d, text.measureText(directionLabel) + 28f * d)
            out += Tool.DIRECTION to RectF(right - dirW, 0f, right, h); right -= dirW
            val toneW = maxOf(52f * d, text.measureText(toneLabel) + 28f * d)
            out += Tool.TONE to RectF(right - toneW, 0f, right, h); right -= toneW
            out += Tool.TRANSLATE to RectF(right - 52f * d, 0f, right, h)
        }
        return out
    }

    private fun drawIcon(c: Canvas, res: Int, cx: Float, cy: Float, sizeDp: Float, color: Int, rotate: Float = 0f) {
        val dr = icon(res)
        val s = (sizeDp * d).toInt()
        dr.setBounds((cx - s / 2f).toInt(), (cy - s / 2f).toInt(), (cx + s / 2f).toInt(), (cy + s / 2f).toInt())
        dr.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        c.save()
        if (rotate != 0f) c.rotate(rotate, cx, cy)
        dr.draw(c)
        c.restore()
    }

    override fun onDraw(c: Canvas) {
        val col = colors
        val cy = height / 2f
        for ((tool, r) in layoutItems()) {
            val cx = r.centerX()
            if (pressed == tool) {
                paint.color = col.pressed
                c.drawCircle(cx, cy, 20f * d, paint)
            }
            when (tool) {
                Tool.GRID -> {
                    if (toolsOpen) {
                        paint.color = col.chip
                        c.drawCircle(cx, cy, 17f * d, paint)
                    }
                    drawIcon(c, R.drawable.kb_grid, cx, cy, 25f, col.keyText)
                }
                Tool.MIC -> {
                    paint.color = col.chip
                    c.drawCircle(cx, cy, 17f * d, paint)
                    drawIcon(c, R.drawable.kb_mic, cx, cy, 24f, col.keyText)
                }
                Tool.TRANSLATE -> when {
                    busy -> drawIcon(c, R.drawable.ic_rurdu_progress, cx, cy, 24f, col.keyText, angle)
                    canRevert -> drawIcon(c, R.drawable.kb_undo, cx, cy, 24f, col.keyText)
                    else -> drawIcon(c, R.drawable.kb_translate, cx, cy, 24f, col.keyText)
                }
                Tool.TONE -> label(c, toneLabel, cx, cy, col.keyText)
                Tool.DIRECTION -> label(c, directionLabel, cx, cy, col.keyText)
                else -> {
                    val res = toolIcons.first { it.first == tool }.second
                    drawIcon(c, res, cx, cy, 24f, col.keyText)
                }
            }
        }
    }

    private fun label(c: Canvas, s: String, cx: Float, cy: Float, color: Int) {
        text.color = color
        c.drawText(s, cx, cy + 0.716f * 14f * d / 2f, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        fun hit(): Tool? = layoutItems().firstOrNull { it.second.contains(ev.x, ev.y) }?.first
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = hit(); invalidate() }
            MotionEvent.ACTION_MOVE -> if (pressed != null && hit() != pressed) { pressed = null; invalidate() }
            MotionEvent.ACTION_UP -> {
                val t = pressed
                pressed = null
                invalidate()
                if (t != null && hit() == t) listener?.onTool(t)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        spinner.cancel()
    }
}
