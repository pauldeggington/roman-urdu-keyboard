package com.paul.urdukb

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import kotlin.math.ceil

/** A drawable that draws a short word. Used for the Formal/Casual and direction keys so they say what they do. */
class TextDrawable(context: Context, private val label: String, sizeSp: Float) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
    }
    private val density = context.resources.displayMetrics.density
    private var tint: ColorStateList? = null
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val t = tint
        val color = t?.getColorForState(state, t.defaultColor) ?: 0xFFFFFFFF.toInt()
        paint.color = color
        paint.alpha = (paint.alpha * alphaValue) / 255
        val b = bounds
        val fm = paint.fontMetrics
        canvas.drawText(label, b.exactCenterX(), b.exactCenterY() - (fm.ascent + fm.descent) / 2f, paint)
    }

    override fun getIntrinsicWidth(): Int = ceil(paint.measureText(label)).toInt() + (2 * density).toInt()
    override fun getIntrinsicHeight(): Int = (24 * density).toInt()

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        invalidateSelf()
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
