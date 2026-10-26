package com.paul.urdukb

import android.R as A
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable

/** Material You colours (wallpaper-based on Android 12+), light and dark. */
class Palette(ctx: Context) {
    val dark: Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val c = { id: Int -> ctx.getColor(id) }

    // Keyboard
    val kbBg = c(if (dark) A.color.system_neutral1_900 else A.color.system_neutral1_200)
    val key = c(if (dark) A.color.system_neutral1_700 else A.color.system_neutral1_0)
    val keyPressed = c(if (dark) A.color.system_neutral1_600 else A.color.system_neutral1_300)
    val func = c(if (dark) A.color.system_neutral1_800 else A.color.system_neutral1_300)
    val funcPressed = c(if (dark) A.color.system_neutral1_700 else A.color.system_neutral1_400)
    val kbText = c(if (dark) A.color.system_neutral1_50 else A.color.system_neutral1_900)
    val kbHint = c(if (dark) A.color.system_neutral1_400 else A.color.system_neutral1_500)
    val accent = c(A.color.system_accent1_200)
    val accentPressed = c(A.color.system_accent1_300)
    val onAccent = c(A.color.system_accent1_900)
    val popup = c(if (dark) A.color.system_neutral1_600 else A.color.system_neutral1_0)

    // App
    val appBg = c(if (dark) A.color.system_neutral1_900 else A.color.system_neutral1_50)
    val card = c(if (dark) A.color.system_neutral1_800 else A.color.system_neutral1_0)
    val field = c(if (dark) A.color.system_neutral1_700 else A.color.system_neutral1_100)
    val text = c(if (dark) A.color.system_neutral1_50 else A.color.system_neutral1_900)
    val textMuted = c(if (dark) A.color.system_neutral2_300 else A.color.system_neutral2_600)
    val primary = c(if (dark) A.color.system_accent1_200 else A.color.system_accent1_600)
    val onPrimary = c(if (dark) A.color.system_accent1_800 else A.color.system_accent1_0)
    val tonal = c(if (dark) A.color.system_accent2_700 else A.color.system_accent2_100)
    val onTonal = c(if (dark) A.color.system_accent2_100 else A.color.system_accent2_900)
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
