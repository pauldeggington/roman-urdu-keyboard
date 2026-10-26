package com.paul.urdukb

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.util.concurrent.Executors

/** Setup screen: checklist, model, try it, style per tone, speed, keyboard settings. */
class MainActivity : Activity() {

    private lateinit var pal: Palette
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var cancelFlag = false
    @Volatile private var phase = ""
    private var busy = false
    private var lastSpeedT = 0L
    private var lastSpeedBytes = 0L
    private var speed = 0.0
    private var styleFormal = false

    private lateinit var scroll: ScrollView
    private lateinit var checklist: LinearLayout
    private lateinit var modelCard: View
    private lateinit var tryCard: View

    private lateinit var modelStatus: TextView
    private lateinit var modelDetail: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var tokenEdit: EditText
    private lateinit var urlEdit: EditText
    private lateinit var downloadBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var removeBtn: Button
    private lateinit var verifyBtn: Button

    private lateinit var tryCasual: Button
    private lateinit var tryFormal: Button
    private lateinit var tryInput: EditText
    private lateinit var tryOutput: TextView
    private lateinit var copyBtn: Button

    private lateinit var styleCasual: Button
    private lateinit var styleFormalBtn: Button
    private lateinit var rulesEdit: EditText
    private lateinit var examplesEdit: EditText
    private lateinit var styleBody: LinearLayout

    private lateinit var statsText: TextView
    private lateinit var errorCard: LinearLayout
    private lateinit var errorText: TextView

    private val lockable = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette(this)
        window.statusBarColor = pal.appBg
        window.navigationBarColor = pal.appBg
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (pal.dark) 0 else light, light)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ================================================================ building the screen

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(24f), dpi(16f), dpi(40f))
        }
        root.addView(TextView(this).apply {
            text = "Roman Urdu Keyboard"
            textSize = 28f
            setTextColor(pal.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(muted("Type English, tap Translate in the keyboard's toolbar, get Roman Urdu. Runs fully on your phone.").apply {
            setPadding(0, dpi(4f), 0, dpi(18f))
        })

        root.addView(buildChecklistCard())
        modelCard = buildModelCard().also { root.addView(it) }
        tryCard = buildTryCard().also { root.addView(it) }
        root.addView(buildStyleCard())
        root.addView(buildSpeedCard())
        root.addView(buildKeyboardCard())
        root.addView(buildErrorCard())

        scroll = ScrollView(this).apply {
            setBackgroundColor(pal.appBg)
            isFillViewport = true
            addView(root)
        }
        return scroll
    }

    private fun buildChecklistCard(): View {
        val card = card()
        card.addView(title("Setup"))
        checklist = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(checklist)
        return card
    }

    private fun checkRow(n: Int, done: Boolean, label: String, sub: String, action: String?, onAction: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(12f), 0, 0)
        }
        val size = dpi(30f)
        row.addView(TextView(this).apply {
            text = if (done) "✓" else n.toString()
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (done) pal.onPrimary else pal.onTonal)
            background = roundedBg(if (done) pal.primary else pal.tonal, size / 2f)
        }, LinearLayout.LayoutParams(size, size))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), 0, dpi(8f), 0)
        }
        texts.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(pal.text)
            if (done) alpha = 0.75f
        })
        if (sub.isNotEmpty()) texts.addView(muted(sub).apply { textSize = 12.5f })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null && !done) {
            row.addView(smallButton(action) { onAction() })
        }
        return row
    }

    private fun buildModelCard(): View {
        val card = card()
        card.addView(title("Model"))
        modelStatus = TextView(this).apply {
            textSize = 15f
            setPadding(0, dpi(8f), 0, 0)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        card.addView(modelStatus)
        modelDetail = muted("")
        card.addView(modelDetail)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(pal.primary)
            visibility = View.GONE
            layoutParams = lp().apply { topMargin = dpi(12f) }
        }
        card.addView(progressBar)
        progressText = muted("").apply { visibility = View.GONE }
        card.addView(progressText)

        card.addView(muted("To download: make a free read token at huggingface.co/settings/tokens and accept the Gemma licence on the model page once.").apply {
            setPadding(0, dpi(14f), 0, dpi(6f))
        })
        tokenEdit = edit("Hugging Face token (hf_…)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.token(this@MainActivity))
        }
        card.addView(tokenEdit)

        val row1 = row()
        downloadBtn = button("Download model", true) { startDownload() }
        cancelBtn = button("Cancel", false, lock = false) { cancelFlag = true }.apply { visibility = View.GONE }
        row1.addView(downloadBtn, rowLp())
        row1.addView(cancelBtn, rowLp())
        card.addView(row1)

        val row2 = row()
        row2.addView(button("Use a file on this phone", false) {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                },
                REQ_MODEL,
            )
        }, rowLp())
        removeBtn = button("Remove", false) { removeModel() }
        row2.addView(removeBtn, rowLp())
        card.addView(row2)
        verifyBtn = button("Check the file is intact", false) { runVerify() }
        card.addView(verifyBtn, lp().apply { topMargin = dpi(10f) })

        urlEdit = edit("Model URL").apply {
            setText(Prefs.url(this@MainActivity))
            visibility = View.GONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        card.addView(muted("Advanced: model URL").apply {
            setTextColor(pal.primary)
            setPadding(0, dpi(14f), 0, dpi(4f))
            setOnClickListener { urlEdit.visibility = if (urlEdit.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        })
        card.addView(urlEdit)
        return card
    }

    private fun buildTryCard(): View {
        val card = card()
        card.addView(title("Try it"))
        val seg = row()
        tryCasual = button("Casual · tum", false, lock = false) { setTone(false) }
        tryFormal = button("Formal · aap", false, lock = false) { setTone(true) }
        seg.addView(tryCasual, rowLp())
        seg.addView(tryFormal, rowLp())
        card.addView(seg)
        card.addView(muted("Same switch as the smiley / briefcase key in the keyboard toolbar.").apply {
            setPadding(0, dpi(6f), 0, 0)
            textSize = 12.5f
        })
        tryInput = edit("Type some English").apply {
            minLines = 2
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            layoutParams = lp().apply { topMargin = dpi(12f) }
        }
        card.addView(tryInput)
        val r = row()
        r.addView(button("Translate", true) { tryTranslate() }, rowLp())
        copyBtn = button("Copy", false, lock = false) {
            val t = tryOutput.tag as? String ?: return@button
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Roman Urdu", t))
            toast("Copied")
        }
        r.addView(copyBtn, rowLp())
        card.addView(r)
        tryOutput = TextView(this).apply {
            textSize = 18f
            setTextColor(pal.text)
            setTextIsSelectable(true)
            setPadding(0, dpi(14f), 0, 0)
        }
        card.addView(tryOutput)
        return card
    }

    private fun buildStyleCard(): View {
        val card = card()
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(title("Style"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = muted("Edit").apply { setTextColor(pal.primary); textSize = 15f }
        header.addView(toggle)
        header.setOnClickListener {
            styleBody.visibility = if (styleBody.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            toggle.text = if (styleBody.visibility == View.VISIBLE) "Hide" else "Edit"
        }
        card.addView(header)
        card.addView(muted("The rules and examples the model follows, separately for Casual and Formal."))

        styleBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val seg = row()
        styleCasual = button("Casual", false, lock = false) { showStyle(false) }
        styleFormalBtn = button("Formal", false, lock = false) { showStyle(true) }
        seg.addView(styleCasual, rowLp())
        seg.addView(styleFormalBtn, rowLp())
        styleBody.addView(seg)
        rulesEdit = edit("Rules", multi = true)
        examplesEdit = edit("English | Roman Urdu, one per line", multi = true)
        styleBody.addView(label("Rules"))
        styleBody.addView(rulesEdit)
        styleBody.addView(label("Examples (English | Roman Urdu, one per line)"))
        styleBody.addView(examplesEdit)
        val r = row()
        r.addView(button("Save", true) {
            Prefs.setStyle(this, styleFormal, rulesEdit.text.toString(), examplesEdit.text.toString())
            toast(if (styleFormal) "Formal style saved" else "Casual style saved")
        }, rowLp())
        r.addView(button("Reset", false) {
            Prefs.resetStyle(this, styleFormal)
            showStyle(styleFormal)
            toast("Back to the default")
        }, rowLp())
        styleBody.addView(r)
        card.addView(styleBody)
        showStyle(false)
        return card
    }

    private fun showStyle(formal: Boolean) {
        styleFormal = formal
        rulesEdit.setText(Prefs.rules(this, formal))
        examplesEdit.setText(Prefs.examples(this, formal))
        paintSegment(styleCasual, !formal)
        paintSegment(styleFormalBtn, formal)
    }

    private fun buildSpeedCard(): View {
        val card = card()
        card.addView(title("Model and speed"))
        statsText = TextView(this).apply {
            textSize = 14f
            setTextColor(pal.text)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dpi(8f), 0, 0)
        }
        card.addView(statsText)
        card.addView(Switch(this).apply {
            text = "Use the GPU (try this if translating is slow)"
            setTextColor(pal.text)
            textSize = 14f
            isChecked = Prefs.gpu(this@MainActivity)
            setPadding(0, dpi(12f), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                LlmEngine.setUseGpu(this@MainActivity, checked)
                toast("Takes effect on the next translation")
                refresh()
            }
        })
        card.addView(muted("If the GPU does not work on your phone it falls back to the CPU on its own."))
        card.addView(button("Reset speed numbers", false) { Prefs.resetStats(this); refresh() }, lp().apply { topMargin = dpi(10f) })
        return card
    }

    private fun buildKeyboardCard(): View {
        val card = card()
        card.addView(title("Keyboard"))
        card.addView(muted("Built on HeliBoard. Translate and the tone switch are pinned in the toolbar above the keys. Long-press either one to open this app.").apply {
            setPadding(0, dpi(6f), 0, 0)
        })
        val r = row()
        r.addView(button("Turn on", true) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, rowLp())
        r.addView(button("Switch to it", false) { showPicker() }, rowLp())
        card.addView(r)
        card.addView(button("Keyboard settings: look, layout, glide typing", false) {
            startActivity(Intent(this, helium314.keyboard.settings.SettingsActivity::class.java))
        }, lp().apply { topMargin = dpi(10f) })
        card.addView(muted("Glide typing needs Google's swipe library file, which HeliBoard cannot ship. You can load it under Keyboard settings → Advanced.").apply {
            setPadding(0, dpi(10f), 0, 0)
            textSize = 12.5f
        })
        return card
    }

    private fun buildErrorCard(): View {
        errorCard = card()
        errorCard.visibility = View.GONE
        errorCard.addView(title("Last error").apply { setTextColor(pal.error) })
        errorText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(pal.text)
            setTextIsSelectable(true)
            setPadding(0, dpi(8f), 0, dpi(8f))
        }
        errorCard.addView(errorText)
        val r = row()
        r.addView(button("Copy", true, lock = false) {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("error", Prefs.lastError(this)))
            toast("Copied")
        }, rowLp())
        r.addView(button("Clear", false, lock = false) { Prefs.setLastError(this, ""); refresh() }, rowLp())
        errorCard.addView(r)
        return errorCard
    }

    // ================================================================ small view helpers

    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun rowLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpi(8f) }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = lp().apply { topMargin = dpi(10f) }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(pal.card, dpf(24f))
        setPadding(dpi(20f), dpi(18f), dpi(20f), dpi(18f))
        layoutParams = lp().apply { bottomMargin = dpi(12f) }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTextColor(pal.text)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun muted(t: String) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(pal.textMuted)
    }

    private fun label(t: String) = muted(t).apply { setPadding(0, dpi(12f), 0, dpi(4f)) }

    private fun edit(hint: String, multi: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setTextColor(pal.text)
        setHintTextColor(pal.textMuted)
        textSize = 14f
        background = roundedBg(pal.field, dpf(14f))
        setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
        layoutParams = lp().apply { topMargin = dpi(4f) }
        if (multi) {
            minLines = 4
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
    }

    private fun styleButton(b: Button, filled: Boolean) {
        b.setTextColor(if (filled) pal.onPrimary else pal.onTonal)
        b.background = roundedBg(if (filled) pal.primary else pal.tonal, dpf(100f))
    }

    private fun paintSegment(b: Button, selected: Boolean) = styleButton(b, selected)

    private fun button(label: String, filled: Boolean, lock: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            stateListAnimator = null
            elevation = 0f
            minHeight = dpi(44f)
            minimumHeight = dpi(44f)
            setPadding(dpi(14f), 0, dpi(14f), 0)
            setOnClickListener { onClick() }
        }
        styleButton(b, filled)
        if (lock) lockable += b
        return b
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        stateListAnimator = null
        minHeight = dpi(36f)
        minimumHeight = dpi(36f)
        minWidth = 0
        minimumWidth = 0
        setPadding(dpi(14f), 0, dpi(14f), 0)
        setTextColor(pal.onPrimary)
        background = roundedBg(pal.primary, dpf(100f))
        setOnClickListener { onClick() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun gb(bytes: Long) = String.format("%.2f GB", bytes / 1073741824.0)

    private fun secs(ms: Long) = String.format("%.1f s", ms / 1000.0)

    private fun showPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    private fun scrollTo(v: View) = scroll.post { scroll.smoothScrollTo(0, v.top - dpi(12f)) }

    // ================================================================ state

    private fun setTone(formal: Boolean) {
        prefs().edit { putBoolean(RomanUrdu.PREF_TONE, formal) }
        paintSegment(tryCasual, !formal)
        paintSegment(tryFormal, formal)
    }

    private fun refresh() {
        val f = LlmEngine.modelFile(this)
        val part = ModelDownloader.partFile(f)
        val ok = LlmEngine.modelExists(this)
        val expected = Prefs.modelSize(this)
        val verified = f.exists() && Prefs.verifiedStamp(this) == Prefs.stamp(f)
        val damaged = f.exists() && Prefs.damagedStamp(this) == Prefs.stamp(f)

        when {
            damaged -> {
                modelStatus.text = "Damaged file  •  ${gb(f.length())}"
                modelStatus.setTextColor(pal.error)
                modelDetail.text = "Right size, but its contents do not match Hugging Face's copy. Remove it and download again."
            }
            ok -> {
                modelStatus.text = (if (verified) "Ready and verified" else "Ready") + "  •  ${gb(f.length())}"
                modelStatus.setTextColor(pal.ok)
                modelDetail.text = if (verified) "Gemma 4 E2B. Checksum matches Hugging Face."
                    else "Not checked yet. Tap “Check the file is intact” if loading fails."
            }
            f.exists() -> {
                modelStatus.text = "Model file looks incomplete"
                modelStatus.setTextColor(pal.error)
                modelDetail.text = "${gb(f.length())} on disk" + (if (expected > 0) ", expected ${gb(expected)}." else ".") +
                    " Remove it and download again."
            }
            part.exists() && part.length() > 0 -> {
                modelStatus.text = "Partial download  •  ${gb(part.length())}"
                modelStatus.setTextColor(pal.text)
                modelDetail.text = "Tap Resume to carry on where it stopped."
            }
            else -> {
                modelStatus.text = "No model yet"
                modelStatus.setTextColor(pal.text)
                modelDetail.text = "About 2.6 GB. Download it once over Wi-Fi."
            }
        }
        downloadBtn.text = when {
            !ok && part.exists() && part.length() > 0 -> "Resume download"
            ok -> "Download again"
            else -> "Download model"
        }
        removeBtn.visibility = if (f.exists() || part.exists()) View.VISIBLE else View.GONE
        verifyBtn.visibility = if (f.exists() && !verified) View.VISIBLE else View.GONE

        // keyboard state
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        val tried = Prefs.count(this) > 0

        // checklist
        checklist.removeAllViews()
        checklist.addView(checkRow(1, ok && verified, "Get the model", if (ok && !verified) "Installed, not checked yet" else "Gemma 4 E2B, about 2.6 GB", "Get") {
            scrollTo(modelCard)
        })
        checklist.addView(checkRow(2, enabled, "Turn on the keyboard", "Settings → On-screen keyboards → Roman Urdu Keyboard", "Turn on") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        checklist.addView(checkRow(3, current, "Switch to it", "Or use the keyboard icon at the bottom of the screen", "Switch") { showPicker() })
        checklist.addView(checkRow(4, tried, "Translate something", "Use Try it below, or the Translate key in any app", "Try") {
            scrollTo(tryCard)
            tryInput.requestFocus()
        })

        // tone
        val formal = RomanUrdu.isFormal(this)
        paintSegment(tryCasual, !formal)
        paintSegment(tryFormal, formal)
        copyBtn.visibility = if (tryOutput.tag is String) View.VISIBLE else View.GONE

        // stats
        val n = Prefs.count(this)
        val backend = Prefs.backendUsed(this)
        statsText.text = buildString {
            append("Model file: ").append(if (f.exists()) gb(f.length()) else "none").append('\n')
            append("Running on: ").append(
                when {
                    backend.isEmpty() -> "not loaded yet"
                    Prefs.gpu(this@MainActivity) && backend == "CPU" -> "CPU (GPU did not start)"
                    else -> backend
                }
            ).append('\n')
            append("Load time: ").append(if (Prefs.loadMs(this@MainActivity) > 0) secs(Prefs.loadMs(this@MainActivity)) else "–").append('\n')
            append("Last translation: ").append(if (n > 0) secs(Prefs.lastMs(this@MainActivity)) else "–").append('\n')
            append("Average: ").append(if (n > 0) secs(Prefs.totalMs(this@MainActivity) / n) else "–")
            append("  over ").append(n).append(if (n == 1L) " translation" else " translations")
        }

        val err = Prefs.lastError(this)
        errorCard.visibility = if (err.isNotBlank()) View.VISIBLE else View.GONE
        errorText.text = err

        setBusy(busy)
    }

    private fun setBusy(b: Boolean) {
        busy = b
        for (btn in lockable) {
            btn.isEnabled = !b
            btn.alpha = if (b) 0.5f else 1f
        }
        cancelBtn.visibility = if (b && progressBar.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (b) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ================================================================ downloads and checks

    private fun onProgress(done: Long, total: Long) {
        val now = System.nanoTime()
        if (lastSpeedT == 0L) { lastSpeedT = now; lastSpeedBytes = done }
        val dt = (now - lastSpeedT) / 1e9
        if (dt >= 1.0) {
            speed = (done - lastSpeedBytes) / dt
            lastSpeedT = now
            lastSpeedBytes = done
        }
        val label = phase
        ui.post {
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            if (total > 0) progressBar.progress = (done * 1000 / total).toInt()
            val ofTotal = if (total > 0) " of ${gb(total)}" else ""
            val sp = if (speed > 0) "  •  " + String.format("%.1f MB/s", speed / 1048576.0) else ""
            progressText.text = label + gb(done) + ofTotal + sp
        }
    }

    private fun beginTransfer(message: String) {
        cancelFlag = false
        lastSpeedT = 0L
        speed = 0.0
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressText.text = message
        setBusy(true)
    }

    private fun endTransfer(error: Throwable?) {
        phase = ""
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
        setBusy(false)
        if (error is ModelDownloader.CancelledException) toast("Stopped. Tap Resume to carry on.")
        else if (error != null) Prefs.setLastError(this, "${error.javaClass.simpleName}: ${error.message}")
        refresh()
    }

    /** Runs on the io thread. Records Verified / Damaged for the current file. */
    private fun verifyInline(file: File, token: String) {
        phase = "Checking: "
        lastSpeedT = 0L
        speed = 0.0
        ui.post { progressBar.progress = 0; progressText.text = "Checking the file is intact…" }
        val verdict = ModelDownloader.verify(file, Prefs.url(this), token, { d, t -> onProgress(d, t) }) { cancelFlag }
        phase = ""
        val stamp = Prefs.stamp(file)
        when (verdict) {
            is ModelDownloader.Verdict.Ok -> {
                Prefs.setVerifiedStamp(this, stamp)
                Prefs.setDamagedStamp(this, "")
            }
            is ModelDownloader.Verdict.Damaged -> {
                Prefs.setVerifiedStamp(this, "")
                Prefs.setDamagedStamp(this, stamp)
                Prefs.setLastError(this, "The model file is damaged. ${verdict.detail}")
            }
            is ModelDownloader.Verdict.Unknown -> {
                Prefs.setVerifiedStamp(this, "")
                Prefs.setDamagedStamp(this, "")
                ui.post { toast("Could not check the file: ${verdict.reason}") }
            }
        }
    }

    private fun currentToken() = tokenEdit.text.toString().trim().ifEmpty { Prefs.token(this) }

    private fun startDownload() {
        val token = tokenEdit.text.toString().trim()
        if (token.isEmpty()) {
            toast("Paste your Hugging Face token first")
            tokenEdit.requestFocus()
            return
        }
        val dest = LlmEngine.modelFile(this)
        if (dest.parentFile!!.usableSpace < 3L * 1024 * 1024 * 1024 && !ModelDownloader.partFile(dest).exists()) {
            toast("You need about 3 GB of free space")
            return
        }
        Prefs.setToken(this, token)
        Prefs.setUrl(this, urlEdit.text.toString())
        val url = Prefs.url(this)
        Prefs.setLastError(this, "")
        beginTransfer("Starting…")
        io.execute {
            var err: Throwable? = null
            try {
                LlmEngine.reset()
                val size = ModelDownloader.download(url, token, dest, { d, t -> onProgress(d, t) }) { cancelFlag }
                Prefs.setModelSize(this, size)
                verifyInline(dest, token)
            } catch (t: Throwable) {
                err = t
            }
            ui.post { endTransfer(err) }
        }
    }

    private fun runVerify() {
        val f = LlmEngine.modelFile(this)
        if (!f.exists()) return
        val token = currentToken()
        Prefs.setLastError(this, "")
        beginTransfer("Checking the file is intact…")
        io.execute {
            var err: Throwable? = null
            try {
                verifyInline(f, token)
            } catch (t: Throwable) {
                err = t
            }
            ui.post { endTransfer(err) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_MODEL && resultCode == RESULT_OK && uri != null) importModel(uri)
    }

    private fun importModel(uri: Uri) {
        Prefs.setLastError(this, "")
        val token = currentToken()
        beginTransfer("Copying…")
        io.execute {
            var err: Throwable? = null
            try {
                LlmEngine.reset()
                val dest = LlmEngine.modelFile(this)
                val size = ModelDownloader.importFrom(this, uri, dest, { d, t -> onProgress(d, t) }) { cancelFlag }
                Prefs.setModelSize(this, size)
                verifyInline(dest, token)
            } catch (t: Throwable) {
                err = t
            }
            ui.post { endTransfer(err) }
        }
    }

    private fun removeModel() {
        LlmEngine.reset()
        val f = LlmEngine.modelFile(this)
        f.delete()
        ModelDownloader.partFile(f).delete()
        Prefs.setModelSize(this, 0L)
        Prefs.setVerifiedStamp(this, "")
        Prefs.setDamagedStamp(this, "")
        refresh()
    }

    private fun tryTranslate() {
        val text = tryInput.text.toString().trim()
        if (text.isEmpty()) {
            toast("Type something first")
            return
        }
        if (!LlmEngine.modelExists(this)) {
            toast("Get the model first")
            scrollTo(modelCard)
            return
        }
        val formal = RomanUrdu.isFormal(this)
        tryOutput.tag = null
        tryOutput.text = if (LlmEngine.isLoaded()) "Translating…" else "Loading the model, then translating…"
        setBusy(true)
        io.execute {
            var out: String? = null
            var err: Throwable? = null
            try {
                out = LlmEngine.translate(this, text, formal)
            } catch (t: Throwable) {
                err = t
                LlmEngine.saveError(this, t)
            }
            val result = out.orEmpty()
            ui.post {
                if (err != null) {
                    tryOutput.text = "Failed. The details are in Last error at the bottom."
                } else {
                    tryOutput.text = result
                    tryOutput.tag = result
                }
                setBusy(false)
                refresh()
            }
        }
    }

    companion object {
        private const val REQ_MODEL = 1
    }
}
