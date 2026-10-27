package com.paul.urdukb.ime

import android.content.Context
import android.content.SharedPreferences

/** Keyboard settings. One small preferences file, shared by the keyboard and the app. */
object Kb {
    const val PREF_VIBRATE = "vibrate"
    const val PREF_SOUND = "sound"
    const val PREF_AUTOCAP = "autocap"
    const val PREF_DOUBLE_SPACE = "double_space"
    const val PREF_HINTS = "number_hints"
    const val PREF_PREVIEW = "key_preview"

    // Roman Urdu controls
    const val PREF_TONE = "roman_urdu_formal"
    const val PREF_DIRECTION = "roman_urdu_direction"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    fun bool(ctx: Context, key: String, default: Boolean = true) = prefs(ctx).getBoolean(key, default)

    fun vibrate(ctx: Context) = bool(ctx, PREF_VIBRATE, true)
    fun sound(ctx: Context) = bool(ctx, PREF_SOUND, false)
    fun autoCap(ctx: Context) = bool(ctx, PREF_AUTOCAP, true)
    fun doubleSpace(ctx: Context) = bool(ctx, PREF_DOUBLE_SPACE, true)
    fun hints(ctx: Context) = bool(ctx, PREF_HINTS, true)
    fun preview(ctx: Context) = bool(ctx, PREF_PREVIEW, true)
}
