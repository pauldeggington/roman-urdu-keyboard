package com.paul.urdukb

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The keyboard's colours, chosen in the app.
 * "Wallpaper" follows the phone's Material You colours (what Gboard does). Any other swatch builds
 * the keyboard colours from that colour. The keyboard watches this file and redraws when it changes.
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

    private fun settings(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE)

    fun swatchId(ctx: Context): String = settings(ctx).getString("swatch", WALLPAPER) ?: WALLPAPER
    fun mode(ctx: Context): String = settings(ctx).getString("mode", MODE_FOLLOW) ?: MODE_FOLLOW

    fun swatch(ctx: Context): Swatch = SWATCHES.firstOrNull { it.id == swatchId(ctx) } ?: SWATCHES.first()

    /** The colour the app UI is built from, or null for the wallpaper (system) colours. */
    fun seedColor(ctx: Context): Int? = swatch(ctx).takeIf { it.id != WALLPAPER }?.color

    fun tintOf(ctx: Context): Float = swatch(ctx).tint

    /** Saves the choice; the running keyboard picks it up from the preferences file. */
    fun apply(ctx: Context, swatchId: String, mode: String) {
        settings(ctx).edit { putString("swatch", swatchId).putString("mode", mode) }
    }
}
