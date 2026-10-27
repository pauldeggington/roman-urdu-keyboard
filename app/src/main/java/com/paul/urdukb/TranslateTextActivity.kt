package com.paul.urdukb

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * "Translate to English" in the text-selection menu of other apps: select a message someone sent you in Roman Urdu,
 * and a small sheet shows the English. Runs on the phone with the same model as the keyboard.
 */
class TranslateTextActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var pal: Palette
    private lateinit var result: TextView
    private lateinit var note: TextView
    private lateinit var copyBtn: Button
    private var translated: String? = null

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette(this)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { finish(); return }

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(22f))
            background = GradientDrawable().apply {
                setColor(pal.card)
                val r = dp(28f).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            isClickable = true   // taps on the sheet do not close it
        }
        note = TextView(this).apply {
            this.text = "Roman Urdu → English"
            textSize = 13f
            setTextColor(pal.textMuted)
        }
        sheet.addView(note)
        sheet.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(pal.textMuted)
            setPadding(0, dp(8f), 0, dp(12f))
        })
        result = TextView(this).apply {
            textSize = 20f
            setTextColor(pal.text)
            setLineSpacing(0f, 1.15f)
            setTextIsSelectable(true)
            this.text = Words.thinking()
        }
        sheet.addView(result, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(18f), 0, 0) }
        copyBtn = pill("Copy", true) {
            translated?.let {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("English", it))
                note.text = "Copied"
            }
        }.apply { isEnabled = false; alpha = 0.5f }
        row.addView(copyBtn, LinearLayout.LayoutParams(0, dp(46f), 1f).apply { marginEnd = dp(8f) })
        row.addView(pill("Close", false) { finish() }, LinearLayout.LayoutParams(0, dp(46f), 1f))
        sheet.addView(row)

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { finish() }
            addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            // keep the sheet above the gesture bar
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                sheet.setPadding(dp(22f), dp(20f), dp(22f), dp(22f) + bars.bottom)
                insets
            }
        }
        setContentView(root)

        // emulator preview builds have no model: this shows the finished layout for a screenshot (never in real builds)
        if (BuildConfig.PREVIEW && intent.hasExtra("preview_result")) {
            translated = intent.getStringExtra("preview_result")
            result.text = translated
            copyBtn.isEnabled = true
            copyBtn.alpha = 1f
            return
        }
        if (!LlmEngine.modelExists(this)) {
            result.text = "The translator isn't downloaded yet. Open Roman Urdu Keyboard and finish the setup, then try again."
            copyBtn.visibility = View.GONE
            return
        }
        if (!LlmEngine.isLoaded()) note.text = "Roman Urdu → English  ·  loading the model, the first one takes a bit longer"
        Executors.newSingleThreadExecutor().execute {
            var out: String? = null
            var err: Throwable? = null
            try {
                out = LlmEngine.translate(this, text, Style.TO_ENGLISH)
            } catch (t: Throwable) {
                err = t
                LlmEngine.saveError(this, t)
            }
            ui.post {
                if (isFinishing || isDestroyed) return@post
                if (err != null || out.isNullOrBlank()) {
                    result.text = "That didn't work. The details are in the app under Last error."
                } else {
                    translated = out
                    result.text = out
                    note.text = "Roman Urdu → English"
                    copyBtn.isEnabled = true
                    copyBtn.alpha = 1f
                }
            }
        }
    }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        elevation = 0f
        setTextColor(if (filled) pal.onPrimary else pal.onTonal)
        background = roundedBg(if (filled) pal.primary else pal.tonal, dp(100f).toFloat())
        setOnClickListener { onClick() }
    }
}
