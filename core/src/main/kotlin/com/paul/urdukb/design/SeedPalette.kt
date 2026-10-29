package com.paul.urdukb.design

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Every colour the keyboard draws with. */
data class KeyboardRoles(
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
)

/** Every colour the app screens draw with. */
data class AppRoles(
    val dark: Boolean,
    val appBg: Int,
    val card: Int,
    val field: Int,
    val text: Int,
    val textMuted: Int,
    val primary: Int,
    val onPrimary: Int,
    val tonal: Int,
    val onTonal: Int,
)

/**
 * The colours built from a chosen swatch, for the keyboard and for the app screens, from one place. (The "wallpaper"
 * choice uses the phone's own Material You colours instead, so it is not here.) Plain Kotlin, so the contrast of every
 * swatch in light and dark is checked by a unit test.
 */
object SeedPalette {

    /**
     * A button fill of this hue that the given text colour can be read on: starts at the lightness the design wants and
     * darkens only as far as needed to reach the 4.5 contrast limit. Swatches that already pass are unchanged.
     */
    private fun fill(h: Float, s: Float, l: Float, text: Int): Int {
        var ll = l
        var col = Hsl.toArgb(h, s, ll)
        while (Contrast.ratio(text, col) < 4.55 && ll > 0.15f) {
            ll -= 0.01f
            col = Hsl.toArgb(h, s, ll)
        }
        return col
    }

    fun keyboard(seed: Int, tint: Float, dark: Boolean): KeyboardRoles {
        val h = Hsl.hue(seed)
        val k = tint
        val kk = k.coerceAtLeast(0.4f)
        fun c(s: Float, l: Float) = Hsl.toArgb(h, s, l)
        return if (dark) KeyboardRoles(
            dark = true,
            bg = c(0.18f * k, 0.11f),
            keyText = c(0.30f * k, 0.93f),
            hint = c(0.16f * k, 0.62f),
            pill = c(0.20f * k, 0.255f),
            pillText = c(0.30f * k, 0.90f),
            space = c(0.14f * k, 0.19f),
            action = c(0.55f * kk, 0.78f),
            actionText = c(0.55f * kk, 0.14f),
            popupBg = c(0.16f * k, 0.24f),
            popupText = c(0.30f * k, 0.93f),
            selected = c(0.55f * kk, 0.78f),
            selectedText = c(0.55f * kk, 0.14f),
            pressed = c(0.16f * k, 0.22f),
            chip = c(0.14f * k, 0.19f),
        ) else KeyboardRoles(
            dark = false,
            bg = c(0.30f * k, 0.95f),
            keyText = c(0.30f * k, 0.12f),
            hint = c(0.14f * k, 0.40f),
            pill = c(0.32f * k, 0.86f),
            pillText = c(0.40f * k, 0.18f),
            space = c(0.24f * k, 0.89f),
            action = fill(h, 0.55f * kk, 0.42f, c(0.30f * k, 0.99f)),
            actionText = c(0.30f * k, 0.99f),
            popupBg = c(0.30f * k, 0.99f),
            popupText = c(0.30f * k, 0.12f),
            selected = fill(h, 0.55f * kk, 0.42f, c(0.30f * k, 0.99f)),
            selectedText = c(0.30f * k, 0.99f),
            pressed = c(0.24f * k, 0.88f),
            chip = c(0.24f * k, 0.89f),
        )
    }

    fun app(seed: Int, tint: Float, dark: Boolean): AppRoles {
        val h = Hsl.hue(seed)
        val k = tint
        fun c(l: Float, s: Float) = Hsl.toArgb(h, s, l)
        return if (dark) AppRoles(
            dark = true,
            appBg = c(0.09f, 0.20f * k),
            card = c(0.14f, 0.18f * k),
            field = c(0.19f, 0.18f * k),
            text = c(0.93f, 0.30f * k),
            textMuted = c(0.66f, 0.14f * k),
            primary = c(0.76f, 0.70f * k.coerceAtLeast(0.35f)),
            onPrimary = c(0.14f, 0.50f * k),
            tonal = c(0.27f, 0.30f * k),
            onTonal = c(0.90f, 0.50f * k),
        ) else AppRoles(
            dark = false,
            appBg = c(0.97f, 0.40f * k),
            card = c(0.995f, 0.30f * k),
            field = c(0.93f, 0.30f * k),
            text = c(0.12f, 0.30f * k),
            textMuted = c(0.40f, 0.14f * k),
            primary = fill(h, 0.60f * k.coerceAtLeast(0.35f), 0.40f, c(0.99f, 0.30f * k)),
            onPrimary = c(0.99f, 0.30f * k),
            tonal = c(0.89f, 0.55f * k),
            onTonal = c(0.18f, 0.55f * k),
        )
    }
}

/** Hue, saturation, lightness colour maths (the same as AndroidX's ColorUtils, so colours do not change). */
object Hsl {
    fun hue(argb: Int): Float {
        val rf = ((argb shr 16) and 0xFF) / 255f
        val gf = ((argb shr 8) and 0xFF) / 255f
        val bf = (argb and 0xFF) / 255f
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val d = mx - mn
        if (mx == mn) return 0f
        var h = when (mx) {
            rf -> ((gf - bf) / d) % 6f
            gf -> ((bf - rf) / d) + 2f
            else -> ((rf - gf) / d) + 4f
        }
        h = (h * 60f) % 360f
        if (h < 0) h += 360f
        return h.coerceIn(0f, 360f)
    }

    fun toArgb(h: Float, s: Float, l: Float): Int {
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        val c = (1f - abs(2 * ll - 1f)) * ss
        val m = ll - 0.5f * c
        val x = c * (1f - abs((h / 60f % 2f) - 1f))
        var r = 0; var g = 0; var b = 0
        when (h.toInt() / 60) {
            0 -> { r = (255 * (c + m)).roundToInt(); g = (255 * (x + m)).roundToInt(); b = (255 * m).roundToInt() }
            1 -> { r = (255 * (x + m)).roundToInt(); g = (255 * (c + m)).roundToInt(); b = (255 * m).roundToInt() }
            2 -> { r = (255 * m).roundToInt(); g = (255 * (c + m)).roundToInt(); b = (255 * (x + m)).roundToInt() }
            3 -> { r = (255 * m).roundToInt(); g = (255 * (x + m)).roundToInt(); b = (255 * (c + m)).roundToInt() }
            4 -> { r = (255 * (x + m)).roundToInt(); g = (255 * m).roundToInt(); b = (255 * (c + m)).roundToInt() }
            else -> { r = (255 * (c + m)).roundToInt(); g = (255 * m).roundToInt(); b = (255 * (x + m)).roundToInt() }
        }
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
}

/** WCAG contrast between two colours: 1 (none) to 21 (black on white). Normal text needs 4.5, large text and icons 3. */
object Contrast {
    private fun lin(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(argb: Int) =
        0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
