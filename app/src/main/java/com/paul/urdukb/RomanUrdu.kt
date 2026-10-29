package com.paul.urdukb

import com.paul.urdukb.R
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.paul.urdukb.ime.KbSettings
import java.util.concurrent.Executors

/**
 * The Roman Urdu controls on the toolbar.
 * Translate: replaces the whole text field with Roman Urdu. Tapping it again (before typing anything else)
 * puts the English back. Tone: Auto (guessed from the sentence) or one of Casual, Formal, Poetic, Funny, Respectful.
 */
object RomanUrdu {

    /** The keyboard sets this so its toolbar redraws when translating starts, ends or can be undone. */
    @Volatile var onStateChanged: (() -> Unit)? = null

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

    fun isFormal(ctx: Context): Boolean = KbSettings.of(ctx).formalTone

    fun direction(ctx: Context): String = KbSettings.of(ctx).direction

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
        if (toEnglish) return Style.TO_ENGLISH
        val usual = if (isFormal(ctx)) Style.FORMAL else Style.CASUAL
        val choice = toneChoice(ctx)
        return if (choice == TONE_AUTO) ToneGuess.guess(text, usual) else Style.fromKey(choice)?.takeIf { it.isTone } ?: usual
    }

    const val TONE_AUTO = "auto"

    /** "auto" or the key of one tone. */
    fun toneChoice(ctx: Context): String = KbSettings.of(ctx).toneChoice

    /** The name of a tone, as shown on the toolbar and in messages. */
    fun toneName(style: Style): String = when (style) {
        Style.CASUAL -> "Casual"
        Style.FORMAL -> "Formal"
        Style.POETIC -> "Poetic"
        Style.FUNNY -> "Funny"
        Style.RESPECTFUL -> "Respectful"
        Style.TO_ENGLISH -> "English"
    }

    /** Short word shown on the toolbar: Auto, or the tone chosen by hand. */
    fun toneLabel(ctx: Context): String {
        val choice = toneChoice(ctx)
        return if (choice == TONE_AUTO) "Auto" else Style.fromKey(choice)?.let { toneName(it) } ?: "Auto"
    }

    /** Short word shown on the toolbar: Auto, EN\u2192RU or RU\u2192EN. */
    fun directionShort(ctx: Context): String = when (direction(ctx)) {
        DIR_TO_RU -> "EN\u2192RU"
        DIR_TO_EN -> "RU\u2192EN"
        else -> "Auto"
    }

    private fun refreshToolbar(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        main.post { onStateChanged?.invoke() }
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }

    /** Tapping the tone chip steps through Auto and the five tones. */
    fun cycleTone(ime: Context) {
        val order = listOf(TONE_AUTO) + Style.TONES.map { it.key }
        val next = order[(order.indexOf(toneChoice(ime)).coerceAtLeast(0) + 1) % order.size]
        val settings = KbSettings.of(ime)
        settings.toneChoice = next
        // Casual and Formal are also the usual register that Auto starts from
        if (next == Style.CASUAL.key) settings.formalTone = false
        if (next == Style.FORMAL.key) settings.formalTone = true
        refreshToolbar(ime)
        toast(
            ime,
            when (next) {
                TONE_AUTO -> "Auto tone: the tone is picked from what you wrote"
                Style.CASUAL.key -> "Casual tone (tum): friendly, everyday"
                Style.FORMAL.key -> "Formal tone (aap): polite and respectful"
                Style.POETIC.key -> "Poetic tone: shayari-style, for feelings"
                Style.FUNNY.key -> "Funny tone: playful and teasing"
                else -> "Respectful tone: for elders and people of rank"
            }
        )
    }

    fun cycleDirection(ime: Context) {
        val next = when (direction(ime)) {
            DIR_AUTO -> DIR_TO_RU
            DIR_TO_RU -> DIR_TO_EN
            else -> DIR_AUTO
        }
        KbSettings.of(ime).direction = next
        refreshToolbar(ime)
        toast(
            ime,
            when (next) {
                DIR_TO_RU -> "Always English \u2192 Roman Urdu"
                DIR_TO_EN -> "Always Roman Urdu \u2192 English"
                else -> "Auto: the language is detected for you"
            }
        )
    }

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
            toast(ctx, ctx.getString(R.string.type_first))
            return
        }
        if (ctx.getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            toast(ctx, ctx.getString(R.string.unlock_first))
            return
        }
        if (!LlmEngine.modelExists(ctx)) {
            toast(ctx, ctx.getString(R.string.no_model))
            return
        }

        val style = styleFor(ctx, text)
        val label = if (style == Style.TO_ENGLISH) "Roman Urdu \u2192 English"
            else "English \u2192 Roman Urdu (${toneName(style).lowercase()})"
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
                    failure != null -> toast(ctx, ctx.getString(R.string.translation_failed))
                    translated.isBlank() -> toast(ctx, ctx.getString(R.string.model_nothing))
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
