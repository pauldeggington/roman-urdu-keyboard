package com.paul.urdukb

import android.content.ClipboardManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.Executors

/**
 * Gboard-style keyboard with a Translate button.
 * Tap Translate: everything in the text field becomes Roman Urdu (on-device). Tap Undo to restore it.
 */
class UrduKeyboardService : InputMethodService(), KeyboardView.Host {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var kb: KeyboardView? = null
    private var lastOriginal: String? = null

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        kb = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        kb?.refreshTheme()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastOriginal = null
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        kb?.showNumbers(numeric)
        kb?.setCanUndo(false)
        kb?.setError(null)
        kb?.setTranslating(false)
        updateAutoCaps()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps()
    }

    private fun updateAutoCaps() {
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val isText = (ei.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val mode = if (isText) ic.getCursorCapsMode(ei.inputType) else 0
        kb?.setAutoCaps(mode != 0)
    }

    // ---- KeyboardView.Host ----

    override fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        if (text == " ") {
            // Double space becomes ". ", like Gboard.
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                return
            }
        }
        ic.commitText(text, 1)
    }

    override fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(before.length - i, 0)
    }

    override fun enter() {
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun moveCursor(delta: Int) {
        sendDownUpKeyEvents(if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun paste() {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm?.primaryClip
        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
        if (!t.isNullOrEmpty()) currentInputConnection?.commitText(t, 1)
    }

    override fun switchKeyboard() {
        if (!switchToPreviousInputMethod()) showPicker()
    }

    override fun showPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun dismissError() {
        kb?.setError(null)
    }

    override fun translate() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()
        val text = (before + after).trim()

        if (text.isEmpty()) {
            kb?.setError("Type some English first, then tap Translate.")
            return
        }
        if (!LlmEngine.modelExists(this)) {
            kb?.setError("Model not installed. Open the Roman Urdu Keyboard app to download it.")
            return
        }

        kb?.setError(null)
        kb?.setTranslating(true)
        val appCtx = applicationContext

        worker.execute {
            var result: String? = null
            var failure: Throwable? = null
            try {
                result = LlmEngine.translate(appCtx, text)
            } catch (t: Throwable) {
                failure = t
                LlmEngine.saveError(appCtx, t)
            }
            val res: String = result.orEmpty()
            val failed = failure != null
            main.post {
                kb?.setTranslating(false)
                if (failed) {
                    kb?.setError("Translation failed. Open the app to see the details.")
                } else if (res.isBlank()) {
                    kb?.setError("The model returned nothing. Try again.")
                } else {
                    val conn = currentInputConnection
                    if (conn != null) {
                        lastOriginal = before + after
                        conn.beginBatchEdit()
                        conn.deleteSurroundingText(before.length, after.length)
                        conn.commitText(res, 1)
                        conn.endBatchEdit()
                        kb?.setCanUndo(true)
                    }
                }
            }
        }
    }

    override fun undo() {
        val original = lastOriginal ?: return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before.length, after.length)
        ic.commitText(original, 1)
        ic.endBatchEdit()
        lastOriginal = null
        kb?.setCanUndo(false)
    }
}
