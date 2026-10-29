package com.paul.urdukb.ime

import android.R as A
import android.content.Context
import android.content.res.Configuration
import com.paul.urdukb.design.SeedPalette
import com.paul.urdukb.KeyboardLook

/**
 * Keyboard colours. Wallpaper (Material You) tones measured from Gboard on a Pixel 8a:
 * background = neutral variant 900, space bar and mic circle = neutral variant 800,
 * ?123 and Enter pills = secondary 700, text = neutral variant 100.
 * Any other swatch builds the same set from that colour.
 */
data class KbColors(
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

        private fun fromSeed(seed: Int, k: Float, dark: Boolean): KbColors {
            val r = SeedPalette.keyboard(seed, k, dark)
            return KbColors(
                dark = r.dark, bg = r.bg, keyText = r.keyText, hint = r.hint, pill = r.pill, pillText = r.pillText,
                space = r.space, action = r.action, actionText = r.actionText, popupBg = r.popupBg, popupText = r.popupText,
                selected = r.selected, selectedText = r.selectedText, pressed = r.pressed, chip = r.chip,
            )
        }
    }
}
