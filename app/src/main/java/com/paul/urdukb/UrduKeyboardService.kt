package com.paul.urdukb

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Minimal QWERTY keyboard with a Translate button.
 * Tap Translate: everything in the text field becomes Roman Urdu (on-device). Tap Undo to restore it.
 */
class UrduKeyboardService : InputMethodService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var shift = false
    private var symbols = false
    private var lastOriginal: String? = null

    private lateinit var keysContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var translateBtn: Button

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symbolRows = listOf("1234567890", "@#\$%&-+()/", "*\"':;!?")

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        translateBtn = key("Translate", 2f) { translate() }
        top.addView(translateBtn)
        top.addView(key("Undo", 1f) { undo() })
        statusView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 11f
            maxLines = 8
            setPadding(dp(8), 0, dp(8), 0)
        }
        top.addView(statusView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f))
        root.addView(top)

        keysContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysContainer)

        renderKeys()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastOriginal = null
        if (::statusView.isInitialized) {
            statusView.text = if (LlmEngine.modelExists(this)) "" else "Open the app and load the model"
        }
    }

    // ---------- keyboard layout ----------

    private fun renderKeys() {
        keysContainer.removeAllViews()
        val rows = if (symbols) symbolRows else letterRows

        rows.forEachIndexed { i, row ->
            val line = rowLayout()
            val isLastLetterRow = !symbols && i == 2
            if (isLastLetterRow) line.addView(key(if (shift) "⇧" else "⇪", 1.5f) { shift = !shift; renderKeys() })
            row.forEach { c ->
                val label = if (!symbols && shift) c.uppercaseChar().toString() else c.toString()
                line.addView(key(label, 1f) { typeChar(label) })
            }
            if (isLastLetterRow) line.addView(key("⌫", 1.5f) { backspace() })
            keysContainer.addView(line)
        }

        val bottom = rowLayout()
        bottom.addView(key(if (symbols) "ABC" else "123", 1.5f) { symbols = !symbols; shift = false; renderKeys() })
        bottom.addView(key(",", 1f) { typeChar(",") })
        bottom.addView(key("space", 5f) { typeChar(" ") })
        bottom.addView(key(".", 1f) { typeChar(".") })
        if (symbols) bottom.addView(key("⌫", 1.5f) { backspace() })
        bottom.addView(key("⏎", 1.5f) { enter() })
        keysContainer.addView(bottom)
    }

    private fun rowLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun key(label: String, weight: Float, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3A3A3A"))
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- typing ----------

    private fun typeChar(c: String) {
        currentInputConnection?.commitText(c, 1)
        if (shift && !symbols) {
            shift = false
            renderKeys()
        }
    }

    private fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun enter() {
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ---------- translate ----------

    private fun translate() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()
        val text = (before + after).trim()

        if (text.isEmpty()) {
            statusView.text = "Type something first"
            return
        }
        if (!LlmEngine.modelExists(this)) {
            statusView.text = "Open the app and load the model"
            return
        }

        statusView.text = "Translating…"
        translateBtn.isEnabled = false
        val appCtx = applicationContext

        worker.execute {
            val result: String? = try {
                LlmEngine.translate(appCtx, text)
            } catch (e: Throwable) {
                main.post { statusView.text = "Error: ${e.message?.take(400)}" }
                null
            }
            main.post {
                translateBtn.isEnabled = true
                if (!result.isNullOrBlank()) {
                    val conn = currentInputConnection
                    if (conn != null) {
                        lastOriginal = before + after
                        conn.beginBatchEdit()
                        conn.deleteSurroundingText(before.length, after.length)
                        conn.commitText(result, 1)
                        conn.endBatchEdit()
                        statusView.text = ""
                    }
                } else if (result != null) {
                    statusView.text = "Empty result"
                }
            }
        }
    }

    private fun undo() {
        val original = lastOriginal ?: return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before.length, after.length)
        ic.commitText(original, 1)
        ic.endBatchEdit()
        lastOriginal = null
        statusView.text = ""
    }
}
