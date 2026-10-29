package com.paul.urdukb

import android.R as A
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import com.paul.urdukb.design.SeedPalette

/** Colours for the setup app. Wallpaper (Material You) by default, or built from the chosen swatch. */
class Palette(ctx: Context) {
    val dark: Boolean = when (KeyboardLook.mode(ctx)) {
        KeyboardLook.MODE_LIGHT -> false
        KeyboardLook.MODE_DARK -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private val seed: Int? = KeyboardLook.seedColor(ctx)
    private val sys = { id: Int -> ctx.getColor(id) }

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
            val r = SeedPalette.app(seed, KeyboardLook.tintOf(ctx), dark)
            appBg = r.appBg; card = r.card; field = r.field; text = r.text; textMuted = r.textMuted
            primary = r.primary; onPrimary = r.onPrimary; tonal = r.tonal; onTonal = r.onTonal
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
