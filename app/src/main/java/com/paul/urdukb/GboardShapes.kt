package com.paul.urdukb

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.settings.Settings
import kotlin.math.min

/**
 * Shapes measured from Gboard (Material You, no key borders) on a Pixel 8a:
 *  - ?123 / ABC and Enter: tonal pills about 56 x 36 dp, 36 dp is ~67% of the key height
 *  - space bar: a lighter pill, same 36 dp height
 *  - shift, delete, emoji, comma, period: no background
 *  - key press preview and long-press keys: circles
 *  - mic in the toolbar: a 34 dp tonal circle
 */
object GboardShapes {
    const val PILL_HEIGHT = 0.67f
    private const val POPUP_CIRCLE = 0.92f
    private const val MIC_CIRCLE = 0.74f // 34 dp of the 46 dp strip

    /** true when the Rounded style without key borders is in use, which is the Gboard look */
    @JvmStatic
    fun active(): Boolean {
        val colors = Settings.getValues()?.mColors ?: return false
        return colors.themeStyle == KeyboardTheme.STYLE_ROUNDED && !colors.hasKeyBorders
    }

    /** Functional keys that get a pill in Gboard; the others are drawn without a background. */
    @JvmStatic
    fun isPillKey(code: Int) = code == KeyCode.SYMBOL_ALPHA || code == KeyCode.SYMBOL
            || code == KeyCode.ALPHA || code == KeyCode.NUMPAD

    @JvmStatic
    fun pill(inner: Drawable): Drawable = Centered(inner, false, PILL_HEIGHT)

    @JvmStatic
    fun popupCircle(inner: Drawable): Drawable = Centered(inner, true, POPUP_CIRCLE)

    @JvmStatic
    fun micCircle(color: Int): Drawable =
        Centered(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }, true, MIC_CIRCLE)

    private fun dp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    /** Makes the key preview a circle that floats just above the key, like Gboard's popup on keypress. */
    @JvmStatic
    fun styleKeyPreview(view: TextView, keyDrawWidth: Int, keyHeight: Int) {
        val diameter = (keyDrawWidth * 1.3f).toInt().coerceAtLeast(dp(view.context, 40f))
        val bottom = keyHeight + dp(view.context, 3f)
        val bubble = (view.background as? Bubble) ?: Bubble().also { view.background = it }
        bubble.diameter = diameter
        bubble.bottomSpace = bottom
        view.setPadding(0, 0, 0, bottom)
        view.minWidth = diameter
        view.minimumWidth = diameter
        view.minHeight = diameter + bottom
        view.minimumHeight = diameter + bottom
    }

    /** Draws [drawable] as a centered pill (fraction of the height) or circle (fraction of the shorter side). */
    class Centered(inner: Drawable, private val circle: Boolean, private val fraction: Float) : DrawableWrapper(inner) {
        override fun onBoundsChange(bounds: Rect) {
            val d = drawable ?: return
            if (circle) {
                val s = (min(bounds.width(), bounds.height()) * fraction).toInt()
                val l = bounds.centerX() - s / 2
                val t = bounds.centerY() - s / 2
                d.setBounds(l, t, l + s, t + s)
            } else {
                val h = (bounds.height() * fraction).toInt()
                val t = bounds.centerY() - h / 2
                d.setBounds(bounds.left, t, bounds.right, t + h)
            }
        }
        override fun getIntrinsicWidth() = -1
        override fun getIntrinsicHeight() = -1
        override fun getPadding(padding: Rect): Boolean { padding.set(0, 0, 0, 0); return false }
    }

    /** Circle drawn in the space above [bottomSpace]; tinted through the colour filter like the old preview. */
    class Bubble : Drawable() {
        var diameter = 0
        var bottomSpace = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val areaH = b.height() - bottomSpace
            val r = min(b.width(), areaH) / 2f
            canvas.drawCircle(b.exactCenterX(), b.top + areaH / 2f, r, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = diameter
        override fun getPadding(padding: Rect): Boolean { padding.set(0, 0, 0, bottomSpace); return true }
    }
}
