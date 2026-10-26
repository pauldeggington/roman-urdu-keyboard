package com.paul.urdukb

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import java.util.concurrent.Executors

/**
 * The two toolbar keys added to HeliBoard.
 * Translate: replaces the whole text field with Roman Urdu. Tapping it again (before typing anything else)
 * puts the English back. Tone: switches between Casual (tum) and Formal (aap).
 */
object RomanUrdu {

    /** Stored in HeliBoard's own preferences so its toolbar refreshes when these change. */
    const val PREF_TONE = "roman_urdu_formal"
    const val PREF_STATE = "roman_urdu_state"
    const val PREF_DIRECTION = "roman_urdu_direction"

    const val DIR_AUTO = "auto"
    const val DIR_TO_RU = "to_ru"
    const val DIR_TO_EN = "to_en"

    @Volatile var busy = false
        private set
    @Volatile var canRevert = false
        private set

    private var original: String? = null
    private var result: String? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isFormal(ctx: Context): Boolean = ctx.prefs().getBoolean(PREF_TONE, false)

    fun direction(ctx: Context): String = ctx.prefs().getString(PREF_DIRECTION, DIR_AUTO) ?: DIR_AUTO

    /** Level for the toolbar icon: 0 = auto, 1 = English to Roman Urdu, 2 = Roman Urdu to English. */
    fun directionLevel(ctx: Context): Int = when (direction(ctx)) {
        DIR_TO_RU -> 1
        DIR_TO_EN -> 2
        else -> 0
    }

    fun directionLabel(dir: String): String = when (dir) {
        DIR_TO_RU -> "English \u2192 Roman Urdu"
        DIR_TO_EN -> "Roman Urdu \u2192 English"
        else -> "Auto-detect"
    }

    /** Which style to use for this text, given the direction setting and the tone. */
    fun styleFor(ctx: Context, text: String): Style {
        val toEnglish = when (direction(ctx)) {
            DIR_TO_EN -> true
            DIR_TO_RU -> false
            else -> Detect.guess(text) { VariantMap.isKnownVariant(ctx, it) } == Detect.Language.ROMAN_URDU
        }
        return when {
            toEnglish -> Style.TO_ENGLISH
            isFormal(ctx) -> Style.FORMAL
            else -> Style.CASUAL
        }
    }

    /** Short word shown on the key itself: Casual / Formal, and Auto / EN→RU / RU→EN. */
    fun keyLabel(ctx: Context, key: ToolbarKey): String = when (key) {
        ToolbarKey.TONE -> if (isFormal(ctx)) "Formal" else "Casual"
        else -> when (direction(ctx)) {
            DIR_TO_RU -> "EN\u2192RU"
            DIR_TO_EN -> "RU\u2192EN"
            else -> "Auto"
        }
    }

    /** Draws the key's word instead of an icon, so it says what it does. Main thread only. */
    fun applyLabel(button: ImageButton, key: ToolbarKey) {
        val d = TextDrawable(button.context, keyLabel(button.context, key), 11f)
        Settings.getValues().mColors.setColor(d, ColorType.TOOL_BAR_KEY)
        button.setImageDrawable(d)
        button.contentDescription = when (key) {
            ToolbarKey.TONE -> if (isFormal(button.context)) "Formal tone: aap" else "Casual tone: tum"
            else -> directionLabel(direction(button.context))
        }
    }

    private val spinners = java.util.WeakHashMap<View, ObjectAnimator>()

    /** Turns the Translate key into a spinning arc while the model works, and back afterwards. Main thread only. */
    fun applyBusyIndicator(button: ImageButton) {
        val running = spinners[button]
        if (busy) {
            if (running != null) return
            val arc = button.context.getDrawable(helium314.keyboard.latin.R.drawable.ic_rurdu_progress)!!.mutate()
            Settings.getValues().mColors.setColor(arc, ColorType.TOOL_BAR_KEY)
            button.setImageDrawable(arc)
            val anim = ObjectAnimator.ofFloat(button, View.ROTATION, 0f, 360f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
            anim.start()
            spinners[button] = anim
        } else if (running != null) {
            running.cancel()
            spinners.remove(button)
            button.rotation = 0f
            val icon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.TRANSLATE.name, button.context)
            if (icon != null) {
                Settings.getValues().mColors.setColor(icon, ColorType.TOOL_BAR_KEY)
                button.setImageDrawable(icon)
            }
        }
    }

    /** Nudges HeliBoard's toolbar to redraw our two keys. */
    private fun refreshToolbar(ctx: Context) {
        ctx.prefs().edit { putLong(PREF_STATE, System.nanoTime()) }
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }

    fun toggleTone(ime: InputMethodService) {
        val formal = !isFormal(ime)
        ime.prefs().edit { putBoolean(PREF_TONE, formal) }
        toast(
            ime,
            if (formal) "Formal tone (aap): translations to Roman Urdu will be respectful"
            else "Casual tone (tum): translations to Roman Urdu will be friendly"
        )
    }

    fun cycleDirection(ime: InputMethodService) {
        val next = when (direction(ime)) {
            DIR_AUTO -> DIR_TO_RU
            DIR_TO_RU -> DIR_TO_EN
            else -> DIR_AUTO
        }
        ime.prefs().edit { putString(PREF_DIRECTION, next) }
        toast(
            ime,
            when (next) {
                DIR_TO_RU -> "Always English \u2192 Roman Urdu"
                DIR_TO_EN -> "Always Roman Urdu \u2192 English"
                else -> "Auto: the language is detected for you"
            }
        )
    }

    fun openApp(ime: InputMethodService) = openApp(ime, null)

    /** Opens the app, optionally scrolled to a section ("colour"). */
    fun openApp(ctx: Context, section: String?) {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (section != null) intent.putExtra(MainActivity.EXTRA_SECTION, section)
        ctx.startActivity(intent)
    }

    /** Any other key press ends the "tap again to undo" window. Cheap when nothing is pending. */
    fun onOtherKey(ctx: Context) {
        if (!canRevert) return
        canRevert = false
        original = null
        result = null
        refreshToolbar(ctx)
    }

    private fun fieldText(ic: InputConnection): Pair<String, String> {
        val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()
        return before to after
    }

    private fun replaceField(ic: InputConnection, beforeLen: Int, afterLen: Int, text: String) {
        ic.beginBatchEdit()
        ic.finishComposingText()
        ic.deleteSurroundingText(beforeLen, afterLen)
        ic.commitText(text, 1)
        ic.endBatchEdit()
    }

    fun translateOrRevert(ime: InputMethodService, finishComposing: () -> Unit) {
        if (busy) return
        val ctx = ime.applicationContext
        finishComposing()
        val ic = ime.currentInputConnection ?: return
        val (before, after) = fieldText(ic)
        val whole = before + after

        // Second tap: go back to the English.
        val prev = original
        if (canRevert && prev != null && whole == result) {
            replaceField(ic, before.length, after.length, prev)
            canRevert = false
            original = null
            result = null
            refreshToolbar(ctx)
            return
        }

        val text = whole.trim()
        if (text.isEmpty()) {
            toast(ctx, "Type some English first, then tap Translate")
            return
        }
        if (ctx.getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            toast(ctx, "Unlock your phone first")
            return
        }
        if (!LlmEngine.modelExists(ctx)) {
            toast(ctx, "No model yet. Open the Roman Urdu Keyboard app to download it.")
            return
        }

        val style = styleFor(ctx, text)
        val label = when (style) {
            Style.TO_ENGLISH -> "Roman Urdu \u2192 English"
            Style.FORMAL -> "English \u2192 Roman Urdu (formal)"
            Style.CASUAL -> "English \u2192 Roman Urdu (casual)"
        }
        toast(ctx, if (LlmEngine.isLoaded()) label else "$label. Loading the model, the first one takes a few seconds")
        busy = true
        refreshToolbar(ctx)

        worker.execute {
            var out: String? = null
            var failure: Throwable? = null
            try {
                out = LlmEngine.translate(ctx, text, style)
            } catch (t: Throwable) {
                failure = t
                LlmEngine.saveError(ctx, t)
            }
            val translated = out.orEmpty()
            main.post {
                busy = false
                when {
                    failure != null -> toast(ctx, "Translation failed. Open the app to see why.")
                    translated.isBlank() -> toast(ctx, "The model returned nothing. Try again.")
                    else -> {
                        val conn = ime.currentInputConnection
                        if (conn == null) {
                            toast(ctx, "The text field closed before the translation finished")
                        } else {
                            val (b, a) = fieldText(conn)
                            if (b + a != whole) {
                                toast(ctx, "The text changed while translating, so nothing was replaced")
                            } else {
                                replaceField(conn, b.length, a.length, translated)
                                original = whole
                                result = translated
                                canRevert = true
                            }
                        }
                    }
                }
                refreshToolbar(ctx)
            }
        }
    }
}
