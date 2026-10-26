package com.paul.urdukb

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.ColorSetting
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * The keyboard's colours, chosen in the app.
 * "Wallpaper" uses HeliBoard's Material You theme (what Gboard does). Any other swatch builds a
 * light and a dark HeliBoard user theme from that colour and switches the keyboard to them.
 */
object KeyboardLook {

    class Swatch(val id: String, val name: String, val color: Int, val tint: Float = 1f)

    const val WALLPAPER = "wallpaper"

    const val MODE_FOLLOW = "follow"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    val SWATCHES = listOf(
        Swatch(WALLPAPER, "Wallpaper", 0),
        Swatch("mauve", "Mauve", 0xFFC27BA0.toInt()),
        Swatch("rose", "Rose", 0xFFE8618C.toInt()),
        Swatch("coral", "Coral", 0xFFF0735A.toInt()),
        Swatch("terracotta", "Clay", 0xFFC8683F.toInt()),
        Swatch("amber", "Amber", 0xFFE9A23B.toInt()),
        Swatch("lemon", "Lemon", 0xFFD9C533.toInt()),
        Swatch("lime", "Lime", 0xFF8BC34A.toInt()),
        Swatch("green", "Green", 0xFF5FB87A.toInt()),
        Swatch("forest", "Forest", 0xFF3E8E5A.toInt()),
        Swatch("teal", "Teal", 0xFF2FB5A8.toInt()),
        Swatch("cyan", "Cyan", 0xFF36B9D6.toInt()),
        Swatch("sky", "Sky", 0xFF4FA3E0.toInt()),
        Swatch("blue", "Blue", 0xFF3D7BE8.toInt()),
        Swatch("indigo", "Indigo", 0xFF6C7CE8.toInt()),
        Swatch("violet", "Violet", 0xFFA678E3.toInt()),
        Swatch("magenta", "Magenta", 0xFFC653C9.toInt()),
        Swatch("brown", "Brown", 0xFF9C7357.toInt(), tint = 0.55f),
        Swatch("sand", "Sand", 0xFFC9B48A.toInt(), tint = 0.50f),
        Swatch("graphite", "Graphite", 0xFF8A9099.toInt(), tint = 0.18f),
    )

    private const val THEME_LIGHT = "urdu_light"
    private const val THEME_DARK = "urdu_dark"

    private fun settings(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE)

    fun swatchId(ctx: Context): String = settings(ctx).getString("swatch", WALLPAPER) ?: WALLPAPER
    fun mode(ctx: Context): String = settings(ctx).getString("mode", MODE_FOLLOW) ?: MODE_FOLLOW

    fun swatch(ctx: Context): Swatch = SWATCHES.firstOrNull { it.id == swatchId(ctx) } ?: SWATCHES.first()

    /** The colour the app UI is built from, or null for the wallpaper (system) colours. */
    fun seedColor(ctx: Context): Int? = swatch(ctx).takeIf { it.id != WALLPAPER }?.color

    fun tintOf(ctx: Context): Float = swatch(ctx).tint

    private fun hsl(hue: Float, sat: Float, light: Float) =
        ColorUtils.HSLToColor(floatArrayOf(hue, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f)))

    private fun hueOf(color: Int): Float {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(color, out)
        return out[0]
    }

    private fun setting(name: String, color: Int) = ColorSetting(name, false, color)

    /** Dark palette: near-black tinted background, tonal pills, soft light text (like Gboard's dark Material You). */
    private fun darkColors(s: Swatch): List<ColorSetting> {
        val h = hueOf(s.color)
        val k = s.tint
        val text = hsl(h, 0.30f * k, 0.93f)
        val hint = hsl(h, 0.16f * k, 0.62f)
        return listOf(
            // tones measured from Gboard dark on a Pixel 8a: background 11%, ?123/enter pill 25%, space/mic 19%
            setting(KeyboardTheme.COLOR_BACKGROUND, hsl(h, 0.18f * k, 0.11f)),
            setting(KeyboardTheme.COLOR_KEYS, hsl(h, 0.16f * k, 0.16f)),
            setting(KeyboardTheme.COLOR_FUNCTIONAL_KEYS, hsl(h, 0.20f * k, 0.255f)),
            setting(KeyboardTheme.COLOR_SPACEBAR, hsl(h, 0.14f * k, 0.19f)),
            setting(KeyboardTheme.COLOR_ACCENT, hsl(h, 0.20f * k, 0.255f)),
            setting(KeyboardTheme.COLOR_TEXT, text),
            setting(KeyboardTheme.COLOR_SUGGESTION_TEXT, text),
            setting(KeyboardTheme.COLOR_HINT_TEXT, hint),
            setting(KeyboardTheme.COLOR_SPACEBAR_TEXT, hint),
            setting(KeyboardTheme.COLOR_GESTURE, hsl(h, 0.70f * k.coerceAtLeast(0.4f), 0.70f)),
        )
    }

    /** Light palette: pale tinted background, white-ish keys, tonal pills, dark text. */
    private fun lightColors(s: Swatch): List<ColorSetting> {
        val h = hueOf(s.color)
        val k = s.tint
        val text = hsl(h, 0.30f * k, 0.12f)
        val hint = hsl(h, 0.14f * k, 0.40f)
        return listOf(
            setting(KeyboardTheme.COLOR_BACKGROUND, hsl(h, 0.30f * k, 0.95f)),
            setting(KeyboardTheme.COLOR_KEYS, hsl(h, 0.30f * k, 0.98f)),
            setting(KeyboardTheme.COLOR_FUNCTIONAL_KEYS, hsl(h, 0.32f * k, 0.86f)),
            setting(KeyboardTheme.COLOR_SPACEBAR, hsl(h, 0.24f * k, 0.89f)),
            setting(KeyboardTheme.COLOR_ACCENT, hsl(h, 0.32f * k, 0.86f)),
            setting(KeyboardTheme.COLOR_TEXT, text),
            setting(KeyboardTheme.COLOR_SUGGESTION_TEXT, text),
            setting(KeyboardTheme.COLOR_HINT_TEXT, hint),
            setting(KeyboardTheme.COLOR_SPACEBAR_TEXT, hint),
            setting(KeyboardTheme.COLOR_GESTURE, hsl(h, 0.70f * k.coerceAtLeast(0.4f), 0.50f)),
        )
    }

    /** Saves the choice and pushes it into the keyboard's own settings. */
    fun apply(ctx: Context, swatchId: String, mode: String) {
        settings(ctx).edit { putString("swatch", swatchId).putString("mode", mode) }
        val s = SWATCHES.firstOrNull { it.id == swatchId } ?: SWATCHES.first()
        val kb: SharedPreferences = ctx.prefs()

        if (s.id == WALLPAPER) {
            kb.edit {
                putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_DYNAMIC)
                putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DYNAMIC)
                putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
            }
        } else {
            KeyboardTheme.writeUserColors(kb, THEME_LIGHT, lightColors(s))
            KeyboardTheme.writeUserColors(kb, THEME_DARK, darkColors(s))
            kb.edit {
                when (mode) {
                    MODE_LIGHT -> {
                        putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
                        putString(Settings.PREF_THEME_COLORS, THEME_LIGHT)
                    }
                    MODE_DARK -> {
                        putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
                        putString(Settings.PREF_THEME_COLORS, THEME_DARK)
                    }
                    else -> {
                        putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
                        putString(Settings.PREF_THEME_COLORS, THEME_LIGHT)
                        putString(Settings.PREF_THEME_COLORS_NIGHT, THEME_DARK)
                    }
                }
            }
        }
        try {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        } catch (_: Throwable) {
            // the keyboard is not running right now; it reads the new colours when it starts
        }
    }
}
