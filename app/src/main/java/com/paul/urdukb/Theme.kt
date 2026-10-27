package com.paul.urdukb

import android.R as A
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

/** Colours for the setup app. Wallpaper (Material You) by default, or built from the chosen swatch. */
class Palette(ctx: Context) {
    val dark: Boolean = when (KeyboardLook.mode(ctx)) {
        KeyboardLook.MODE_LIGHT -> false
        KeyboardLook.MODE_DARK -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private val seed: Int? = KeyboardLook.seedColor(ctx)
    private val sys = { id: Int -> ctx.getColor(id) }

    private fun hsl(l: Float, s: Float, hue: Float) = ColorUtils.HSLToColor(floatArrayOf(hue, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))

    private val hue: Float = seed?.let {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(it, out)
        out[0]
    } ?: 0f
    private val tint: Float = KeyboardLook.tintOf(ctx)

    val appBg: Int
    val card: Int
    val field: Int
    val text: Int
    val textMuted: Int
    val primary: Int
    val onPrimary: Int
    val tonal: Int
    val onTonal: Int

    init {
        if (seed == null) {
            appBg = sys(if (dark) A.color.system_neutral1_900 else A.color.system_neutral1_50)
            card = sys(if (dark) A.color.system_neutral1_800 else A.color.system_neutral1_0)
            field = sys(if (dark) A.color.system_neutral1_700 else A.color.system_neutral1_100)
            text = sys(if (dark) A.color.system_neutral1_50 else A.color.system_neutral1_900)
            textMuted = sys(if (dark) A.color.system_neutral2_300 else A.color.system_neutral2_600)
            primary = sys(if (dark) A.color.system_accent1_200 else A.color.system_accent1_600)
            onPrimary = sys(if (dark) A.color.system_accent1_800 else A.color.system_accent1_0)
            tonal = sys(if (dark) A.color.system_accent2_700 else A.color.system_accent2_100)
            onTonal = sys(if (dark) A.color.system_accent2_100 else A.color.system_accent2_900)
        } else {
            val k = tint
            appBg = if (dark) hsl(0.09f, 0.20f * k, hue) else hsl(0.97f, 0.40f * k, hue)
            card = if (dark) hsl(0.14f, 0.18f * k, hue) else hsl(0.995f, 0.30f * k, hue)
            field = if (dark) hsl(0.19f, 0.18f * k, hue) else hsl(0.93f, 0.30f * k, hue)
            text = if (dark) hsl(0.93f, 0.30f * k, hue) else hsl(0.12f, 0.30f * k, hue)
            textMuted = if (dark) hsl(0.66f, 0.14f * k, hue) else hsl(0.40f, 0.14f * k, hue)
            primary = if (dark) hsl(0.76f, 0.70f * k.coerceAtLeast(0.35f), hue) else hsl(0.40f, 0.60f * k.coerceAtLeast(0.35f), hue)
            onPrimary = if (dark) hsl(0.14f, 0.50f * k, hue) else hsl(0.99f, 0.30f * k, hue)
            tonal = if (dark) hsl(0.27f, 0.30f * k, hue) else hsl(0.89f, 0.55f * k, hue)
            onTonal = if (dark) hsl(0.90f, 0.50f * k, hue) else hsl(0.18f, 0.55f * k, hue)
        }
    }

    val error = if (dark) 0xFFF2B8B5.toInt() else 0xFFB3261E.toInt()
    val ok = if (dark) 0xFF7FD99A.toInt() else 0xFF1B7F3B.toInt()
}

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun roundedBg(color: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }
