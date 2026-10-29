package com.paul.urdukb

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.paul.urdukb.design.Swatch
import com.paul.urdukb.design.Swatches

/**
 * The keyboard's colours, chosen in the app.
 * "Wallpaper" follows the phone's Material You colours (what Gboard does). Any other swatch builds
 * the keyboard colours from that colour. The keyboard watches this file and redraws when it changes.
 */
object KeyboardLook {

    const val WALLPAPER = Swatches.WALLPAPER

    const val MODE_FOLLOW = "follow"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    val SWATCHES: List<Swatch> get() = Swatches.ALL

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
