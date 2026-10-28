package com.paul.urdukb.ime

import android.content.Context
import android.content.SharedPreferences
import com.paul.urdukb.Secrets

/** Keyboard settings. One small preferences file, shared by the keyboard and the app. */
object Kb {
    const val PREF_VIBRATE = "vibrate"
    const val PREF_SOUND = "sound"
    const val PREF_AUTOCAP = "autocap"
    const val PREF_DOUBLE_SPACE = "double_space"
    const val PREF_HINTS = "number_hints"
    const val PREF_PREVIEW = "key_preview"
    const val PREF_GLIDE = "glide_typing"

    // Roman Urdu controls
    const val PREF_TONE = "roman_urdu_formal"
    const val PREF_DIRECTION = "roman_urdu_direction"

    const val PREF_GIPHY_KEY = "giphy_key"
    const val PREF_CLIP = "clipboard_history"
    const val PREF_WARM = "warm_translator"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    fun bool(ctx: Context, key: String, default: Boolean = true) = prefs(ctx).getBoolean(key, default)

    fun vibrate(ctx: Context) = bool(ctx, PREF_VIBRATE, true)
    fun sound(ctx: Context) = bool(ctx, PREF_SOUND, false)
    fun autoCap(ctx: Context) = bool(ctx, PREF_AUTOCAP, true)
    fun doubleSpace(ctx: Context) = bool(ctx, PREF_DOUBLE_SPACE, true)
    fun hints(ctx: Context) = bool(ctx, PREF_HINTS, true)
    fun preview(ctx: Context) = bool(ctx, PREF_PREVIEW, true)
    fun glide(ctx: Context) = bool(ctx, PREF_GLIDE, true)
    fun warm(ctx: Context) = bool(ctx, PREF_WARM, true)

    /** The settings that change how the keyboard looks or behaves right away; other saved values (recent emoji, clips) do not. */
    val LIVE_KEYS = setOf(PREF_VIBRATE, PREF_SOUND, PREF_AUTOCAP, PREF_DOUBLE_SPACE, PREF_HINTS, PREF_PREVIEW, PREF_GLIDE, PREF_WARM, PREF_TONE, PREF_DIRECTION)

    /** The key the person pasted in the app (decrypted), or empty. */
    fun ownGiphyKey(ctx: Context): String {
        val saved = prefs(ctx).getString(PREF_GIPHY_KEY, "").orEmpty()
        val plain = Secrets.open(saved)
        if (plain.isNotEmpty() && !saved.startsWith("enc1:")) setGiphyKey(ctx, plain)   // older plain value: encrypt it now
        return plain.trim()
    }

    fun setGiphyKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString(PREF_GIPHY_KEY, Secrets.seal(key.trim())).apply()
    }

    /** The GIPHY key to use: the one pasted in the app, else the one built into this release. Empty when neither exists. */
    fun giphyKey(ctx: Context): String = ownGiphyKey(ctx).ifEmpty { com.paul.urdukb.BuildConfig.GIPHY_KEY }
}
