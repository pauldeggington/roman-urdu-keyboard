package com.paul.urdukb.ime

import android.content.Context
import android.content.SharedPreferences
import com.paul.urdukb.BuildConfig
import com.paul.urdukb.Secrets

/**
 * Every keyboard setting in one place, with the name it is saved under and what it is until the person changes it.
 * The keyboard and the app screens both read and write through this class, so a key is never typed twice.
 * They are saved in the keyboard's small preferences file; reading one is a plain in-memory lookup, which is why this
 * is not DataStore (its reads are asynchronous, and the keyboard reads a few settings on every key press).
 */
class KbSettings private constructor(private val sp: SharedPreferences) {

    /** A yes/no setting. */
    class Flag(val key: String, val default: Boolean)

    operator fun get(flag: Flag): Boolean = sp.getBoolean(flag.key, flag.default)
    operator fun set(flag: Flag, value: Boolean) { sp.edit().putBoolean(flag.key, value).apply() }

    // the settings the keyboard reads while typing
    val vibrate get() = this[VIBRATE]
    val sound get() = this[SOUND]
    val autoCap get() = this[AUTOCAP]
    val doubleSpace get() = this[DOUBLE_SPACE]
    val numberHints get() = this[NUMBER_HINTS]
    val keyPreview get() = this[KEY_PREVIEW]
    val glide get() = this[GLIDE]
    val warmTranslator get() = this[WARM_TRANSLATOR]
    val suggest get() = this[SUGGEST]
    val autocorrect get() = this[AUTOCORRECT]
    val clipboardHistory get() = this[CLIPBOARD_HISTORY]

    /** Casual (tum, false) or formal (aap, true) Roman Urdu. */
    var formalTone: Boolean
        get() = sp.getBoolean("roman_urdu_formal", false)
        set(v) { sp.edit().putBoolean("roman_urdu_formal", v).apply() }

    /** "auto" (the app guesses the tone from the sentence) or the key of a tone: casual, formal, poetic, funny, affectionate, respectful. */
    var toneChoice: String
        get() = sp.getString("roman_urdu_tone", "auto") ?: "auto"
        set(v) { sp.edit().putString("roman_urdu_tone", v).apply() }

    /** "auto", "to_ru" or "to_en" (see RomanUrdu). */
    var direction: String
        get() = sp.getString("roman_urdu_direction", "auto") ?: "auto"
        set(v) { sp.edit().putString("roman_urdu_direction", v).apply() }

    /** How long unpinned clips are kept, in hours: 1, 24 or 168. */
    var clipKeepHours: Int
        get() = sp.getInt("clip_ttl_hours", 1).takeIf { it in CLIP_KEEP_CHOICES } ?: 1
        set(v) { sp.edit().putInt("clip_ttl_hours", v).apply() }

    /** The GIPHY key the person pasted in the app (decrypted), or empty. */
    val ownGiphyKey: String
        get() {
            val saved = sp.getString("giphy_key", "").orEmpty()
            val plain = Secrets.open(saved)
            if (plain.isNotEmpty() && !saved.startsWith("enc1:")) setGiphyKey(plain)   // an older plain value: encrypt it now
            return plain.trim()
        }

    fun setGiphyKey(key: String) { sp.edit().putString("giphy_key", Secrets.seal(key.trim())).apply() }

    /** The GIPHY key to use: the one pasted in the app, else the one built into this release. Empty when neither exists. */
    val giphyKey: String get() = ownGiphyKey.ifEmpty { BuildConfig.GIPHY_KEY }

    companion object {
        val VIBRATE = Flag("vibrate", true)
        val SOUND = Flag("sound", false)
        val AUTOCAP = Flag("autocap", true)
        val DOUBLE_SPACE = Flag("double_space", true)
        val NUMBER_HINTS = Flag("number_hints", true)
        val KEY_PREVIEW = Flag("key_preview", true)
        val GLIDE = Flag("glide_typing", true)
        val WARM_TRANSLATOR = Flag("warm_translator", true)
        val SUGGEST = Flag("word_suggestions", true)
        val AUTOCORRECT = Flag("autocorrect", true)
        val CLIPBOARD_HISTORY = Flag("clipboard_history", false)

        val CLIP_KEEP_CHOICES = listOf(1, 24, 168)

        /** The settings that change how the keyboard looks or behaves right away; other saved values (recent emoji, clips) do not. */
        val LIVE_KEYS = setOf(
            VIBRATE, SOUND, AUTOCAP, DOUBLE_SPACE, NUMBER_HINTS, KEY_PREVIEW, GLIDE, WARM_TRANSLATOR, SUGGEST, AUTOCORRECT,
        ).map { it.key }.toSet() + setOf("roman_urdu_formal", "roman_urdu_direction")

        fun of(ctx: Context) = KbSettings(Kb.prefs(ctx))
    }
}
