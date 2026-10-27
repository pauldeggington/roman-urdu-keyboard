package com.paul.urdukb.ime

import android.R as A
import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.ColorUtils
import com.paul.urdukb.KeyboardLook

/**
 * Keyboard colours. Wallpaper (Material You) tones measured from Gboard on a Pixel 8a:
 * background = neutral variant 900, space bar and mic circle = neutral variant 800,
 * ?123 and Enter pills = secondary 700, text = neutral variant 100.
 * Any other swatch builds the same set from that colour.
 */
class KbColors(
    val dark: Boolean,
    val bg: Int,
    val keyText: Int,
    val hint: Int,
    val pill: Int,
    val pillText: Int,
    val space: Int,
    val action: Int,
    val actionText: Int,
    val popupBg: Int,
    val popupText: Int,
    val selected: Int,
    val selectedText: Int,
    val pressed: Int,
    val chip: Int,
) {
    companion object {
        fun of(ctx: Context): KbColors {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (KeyboardLook.mode(ctx)) {
                KeyboardLook.MODE_LIGHT -> false
                KeyboardLook.MODE_DARK -> true
                else -> night
            }
            val seed = KeyboardLook.seedColor(ctx)
            return if (seed == null) wallpaper(ctx, dark) else fromSeed(seed, KeyboardLook.tintOf(ctx), dark)
        }

        private fun wallpaper(ctx: Context, dark: Boolean): KbColors {
            fun c(id: Int) = ctx.getColor(id)
            return if (dark) KbColors(
                dark = true,
                bg = c(A.color.system_neutral2_900),
                keyText = c(A.color.system_neutral2_100),
                hint = c(A.color.system_neutral2_300),
                pill = c(A.color.system_accent2_700),
                pillText = c(A.color.system_accent2_100),
                space = c(A.color.system_neutral2_800),
                action = c(A.color.system_accent1_200),
                actionText = c(A.color.system_accent1_800),
                popupBg = c(A.color.system_neutral2_700),
                popupText = c(A.color.system_neutral2_100),
                selected = c(A.color.system_accent1_200),
                selectedText = c(A.color.system_accent1_800),
                pressed = c(A.color.system_neutral2_700),
                chip = c(A.color.system_neutral2_800),
            ) else KbColors(
                dark = false,
                bg = c(A.color.system_neutral2_50),
                keyText = c(A.color.system_neutral2_900),
                hint = c(A.color.system_neutral2_600),
                pill = c(A.color.system_accent2_100),
                pillText = c(A.color.system_accent2_900),
                space = c(A.color.system_neutral2_100),
                action = c(A.color.system_accent1_600),
                actionText = c(A.color.system_accent1_0),
                popupBg = c(A.color.system_neutral2_0),
                popupText = c(A.color.system_neutral2_900),
                selected = c(A.color.system_accent1_600),
                selectedText = c(A.color.system_accent1_0),
                pressed = c(A.color.system_neutral2_200),
                chip = c(A.color.system_neutral2_100),
            )
        }

        private fun hsl(h: Float, s: Float, l: Float) =
            ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))

        private fun fromSeed(seed: Int, k: Float, dark: Boolean): KbColors {
            val out = FloatArray(3)
            ColorUtils.colorToHSL(seed, out)
            val h = out[0]
            val kk = k.coerceAtLeast(0.4f)
            return if (dark) KbColors(
                dark = true,
                bg = hsl(h, 0.18f * k, 0.11f),
                keyText = hsl(h, 0.30f * k, 0.93f),
                hint = hsl(h, 0.16f * k, 0.62f),
                pill = hsl(h, 0.20f * k, 0.255f),
                pillText = hsl(h, 0.30f * k, 0.90f),
                space = hsl(h, 0.14f * k, 0.19f),
                action = hsl(h, 0.55f * kk, 0.78f),
                actionText = hsl(h, 0.55f * kk, 0.14f),
                popupBg = hsl(h, 0.16f * k, 0.24f),
                popupText = hsl(h, 0.30f * k, 0.93f),
                selected = hsl(h, 0.55f * kk, 0.78f),
                selectedText = hsl(h, 0.55f * kk, 0.14f),
                pressed = hsl(h, 0.16f * k, 0.22f),
                chip = hsl(h, 0.14f * k, 0.19f),
            ) else KbColors(
                dark = false,
                bg = hsl(h, 0.30f * k, 0.95f),
                keyText = hsl(h, 0.30f * k, 0.12f),
                hint = hsl(h, 0.14f * k, 0.40f),
                pill = hsl(h, 0.32f * k, 0.86f),
                pillText = hsl(h, 0.40f * k, 0.18f),
                space = hsl(h, 0.24f * k, 0.89f),
                action = hsl(h, 0.55f * kk, 0.42f),
                actionText = hsl(h, 0.30f * k, 0.99f),
                popupBg = hsl(h, 0.30f * k, 0.99f),
                popupText = hsl(h, 0.30f * k, 0.12f),
                selected = hsl(h, 0.55f * kk, 0.42f),
                selectedText = hsl(h, 0.30f * k, 0.99f),
                pressed = hsl(h, 0.24f * k, 0.88f),
                chip = hsl(h, 0.24f * k, 0.89f),
            )
        }
    }
}
