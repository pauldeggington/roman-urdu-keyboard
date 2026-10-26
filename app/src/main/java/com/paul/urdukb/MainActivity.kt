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
import java.io.File
import java.util.concurrent.Executors

/** Setup screen: get the model, enable the keyboard, try it, tweak the style. */
class MainActivity : Activity() {

    private lateinit var pal: Palette
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var cancelFlag = false
    private var busy = false
    private var lastSpeedT = 0L
    private var lastSpeedBytes = 0L
    private var speed = 0.0

    private lateinit var modelStatus: TextView
    private lateinit var modelDetail: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var tokenEdit: EditText
    private lateinit var urlEdit: EditText
    private lateinit var downloadBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var importBtn: Button
    private lateinit var removeBtn: Button
    private lateinit var verifyBtn: Button
    private var phase = ""
    private lateinit var kbStatus: TextView
    private lateinit var tryInput: EditText
    private lateinit var tryOutput: TextView
    private lateinit var tryBtn: Button
    private lateinit var rulesEdit: EditText
    private lateinit var examplesEdit: EditText
    private lateinit var errorCard: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var styleBody: LinearLayout

    private val allButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette(this)
        window.statusBarColor = pal.appBg
        window.navigationBarColor = pal.appBg
        window.insetsController?.setSystemBarsAppearance(
            if (pal.dark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------------------------------------------------------------- UI building

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(20f), dpi(16f), dpi(32f))
        }

        root.addView(TextView(this).apply {
            text = "Roman Urdu Keyboard"
            textSize = 28f
            setTextColor(pal.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(muted("English to Roman Urdu, fully offline, powered by Gemma 4 on your phone.").apply {
            setPadding(0, dpi(4f), 0, dpi(16f))
        })

        root.addView(buildModelCard())
        root.addView(buildKeyboardCard())
        root.addView(buildTryCard())
        root.addView(buildStyleCard())
        root.addView(buildSpeedCard())
        root.addView(buildErrorCard())

        return ScrollView(this).apply {
            setBackgroundColor(pal.appBg)
            isFillViewport = true
            addView(root)
        }
    }

    private fun buildModelCard(): View {
        val card = card()
        card.addView(title("1. Model"))
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

        card.addView(muted("To download: create a free read token at huggingface.co/settings/tokens and accept the licence on the model's page once.").apply {
            setPadding(0, dpi(14f), 0, dpi(6f))
        })
        tokenEdit = edit("Hugging Face token (hf_...)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.token(this@MainActivity))
        }
        card.addView(tokenEdit)

        val row1 = row()
        downloadBtn = button("Download model", true) { startDownload() }
        cancelBtn = button("Cancel", false) { cancelFlag = true }.apply { visibility = View.GONE }
        row1.addView(downloadBtn, rowLp())
        row1.addView(cancelBtn, rowLp())
        card.addView(row1)

        val row2 = row()
        importBtn = button("Use a file from this phone", false) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                },
                REQ_MODEL,
            )
        }
        removeBtn = button("Remove", false) { removeModel() }
        row2.addView(importBtn, rowLp())
        row2.addView(removeBtn, rowLp())
        card.addView(row2)
        verifyBtn = button("Check the file is intact", false) { runVerify() }
        card.addView(verifyBtn, lp().apply { topMargin = dpi(10f) })

        urlEdit = edit("Model URL").apply {
            setText(Prefs.url(this@MainActivity))
            visibility = View.GONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val adv = muted("Advanced").apply {
            setTextColor(pal.primary)
            setPadding(0, dpi(14f), 0, dpi(4f))
            setOnClickListener {
                urlEdit.visibility = if (urlEdit.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        card.addView(adv)
        card.addView(urlEdit)
        return card
    }

    private fun buildKeyboardCard(): View {
        val card = card()
        card.addView(title("2. Keyboard"))
        kbStatus = muted("").apply { setPadding(0, dpi(6f), 0, dpi(8f)) }
        card.addView(kbStatus)
        val row = row()
        row.addView(button("Enable keyboard", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, rowLp())
        row.addView(button("Switch to it", false) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }, rowLp())
        card.addView(row)
        return card
    }

    private fun buildTryCard(): View {
        val card = card()
        card.addView(title("3. Try it"))
        tryInput = edit("Type some English").apply {
            minLines = 2
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            layoutParams = lp().apply { topMargin = dpi(10f) }
        }
        card.addView(tryInput)
        tryBtn = button("Translate", true) { tryTranslate() }
        card.addView(tryBtn, lp().apply { topMargin = dpi(10f) })
        tryOutput = TextView(this).apply {
            textSize = 17f
            setTextColor(pal.text)
            setTextIsSelectable(true)
            setPadding(0, dpi(12f), 0, 0)
        }
        card.addView(tryOutput)
        return card
    }

    private fun buildStyleCard(): View {
        val card = card()
        val header = title("Style").apply {
            setOnClickListener { toggleStyle() }
        }
        card.addView(header)
        card.addView(muted("Tap to edit the rules and examples the model follows."))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        styleBody = body
        rulesEdit = edit("Rules", true).apply { setText(Prefs.rules(this@MainActivity)) }
        examplesEdit = edit("Examples: English | Roman Urdu (one per line)", true).apply {
            setText(Prefs.examples(this@MainActivity))
        }
        body.addView(label("Rules"))
        body.addView(rulesEdit)
        body.addView(label("Examples (English | Roman Urdu, one per line)"))
        body.addView(examplesEdit)
        val row = row()
        row.addView(button("Save", true) {
            Prefs.setStyle(this, rulesEdit.text.toString(), examplesEdit.text.toString())
            toast("Saved")
        }, rowLp())
        row.addView(button("Reset", false) {
            Prefs.resetStyle(this)
            rulesEdit.setText(Prefs.DEFAULT_RULES)
            examplesEdit.setText(Prefs.DEFAULT_EXAMPLES)
            toast("Back to the defaults")
        }, rowLp())
        body.addView(row)
        card.addView(body)
        return card
    }

    private fun toggleStyle() {
        styleBody.visibility = if (styleBody.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun buildSpeedCard(): View {
        val card = card()
        card.addView(title("Speed"))
        val sw = Switch(this).apply {
            text = "Use the GPU (try this if translating is slow)"
            setTextColor(pal.text)
            textSize = 14f
            isChecked = Prefs.gpu(this@MainActivity)
            setPadding(0, dpi(10f), 0, 0)
            setOnCheckedChangeListener { _, checked -> LlmEngine.setUseGpu(this@MainActivity, checked) }
        }
        card.addView(sw)
        card.addView(muted("If the GPU does not work on your phone it quietly falls back to the CPU."))
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
        val row = row()
        row.addView(button("Copy", true) {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("error", Prefs.lastError(this)))
            toast("Copied")
        }, rowLp())
        row.addView(button("Clear", false) {
            Prefs.setLastError(this, "")
            refresh()
        }, rowLp())
        errorCard.addView(row)
        return errorCard
    }

    // ---------------------------------------------------------------- view helpers

    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun rowLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginEnd = dpi(8f)
    }

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

    private fun button(label: String, filled: Boolean, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            stateListAnimator = null
            elevation = 0f
            minHeight = dpi(44f)
            minimumHeight = dpi(44f)
            setPadding(dpi(16f), 0, dpi(16f), 0)
            setTextColor(if (filled) pal.onPrimary else pal.onTonal)
            background = roundedBg(if (filled) pal.primary else pal.tonal, dpf(100f))
            setOnClickListener { onClick() }
        }
        allButtons += b
        return b
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- state

    private fun gb(bytes: Long) = String.format("%.2f GB", bytes / 1073741824.0)

    private fun refresh() {
        val f = LlmEngine.modelFile(this)
        val part = ModelDownloader.partFile(f)
        val ok = LlmEngine.modelExists(this)
        val expected = Prefs.modelSize(this)

        val verified = f.exists() && Prefs.verifiedStamp(this) == Prefs.stamp(f)
        val damaged = f.exists() && Prefs.damagedStamp(this) == Prefs.stamp(f)
        when {
            damaged -> {
                modelStatus.text = "Damaged file  \u2022  ${gb(f.length())}"
                modelStatus.setTextColor(pal.error)
                modelDetail.text = "It is the right size but its contents do not match Hugging Face's copy. Remove it and download again."
            }
            ok -> {
                modelStatus.text = if (verified) "Ready and verified  \u2022  ${gb(f.length())}" else "Ready  \u2022  ${gb(f.length())}"
                modelStatus.setTextColor(pal.ok)
                modelDetail.text = if (verified) "Checksum matches Hugging Face. Runs on this phone, no internet needed."
                    else "Not checked yet. Tap \u201cCheck the file is intact\u201d if loading fails."
            }
            f.exists() -> {
                modelStatus.text = "Model file looks incomplete"
                modelStatus.setTextColor(pal.error)
                modelDetail.text = "${gb(f.length())} on disk" +
                    (if (expected > 0) ", expected ${gb(expected)}. " else ". ") +
                    "Remove it and download again."
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
        downloadBtn.text = if (!ok && part.exists() && part.length() > 0) "Resume download" else if (ok) "Download again" else "Download model"
        removeBtn.visibility = if (f.exists() || part.exists()) View.VISIBLE else View.GONE
        verifyBtn.visibility = if (f.exists()) View.VISIBLE else View.GONE

        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        kbStatus.text = when {
            current -> "Active. Tap the keyboard icon in any app to switch back to Gboard."
            enabled -> "Enabled. Use “Switch to it”, or the keyboard icon in any app."
            else -> "Not enabled yet. Tap Enable keyboard, then turn on Roman Urdu Translate."
        }

        val err = Prefs.lastError(this)
        errorCard.visibility = if (err.isNotBlank()) View.VISIBLE else View.GONE
        errorText.text = err

        setBusy(busy)
    }

    private fun setBusy(b: Boolean) {
        busy = b
        for (btn in allButtons) {
            btn.isEnabled = !b
            btn.alpha = if (b) 0.5f else 1f
        }
        cancelBtn.visibility = if (b && progressBar.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        cancelBtn.isEnabled = true
        cancelBtn.alpha = 1f
        if (b) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---------------------------------------------------------------- actions

    private fun onProgress(done: Long, total: Long) {
        val now = System.nanoTime()
        if (lastSpeedT == 0L) { lastSpeedT = now; lastSpeedBytes = done }
        val dt = (now - lastSpeedT) / 1e9
        if (dt >= 1.0) {
            speed = (done - lastSpeedBytes) / dt
            lastSpeedT = now
            lastSpeedBytes = done
        }
        ui.post {
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            if (total > 0) progressBar.progress = (done * 1000 / total).toInt()
            val ofTotal = if (total > 0) " of ${gb(total)}" else ""
            val sp = if (speed > 0) "  •  %.1f MB/s".format(speed / 1048576.0) else ""
            progressText.text = "$phase${gb(done)}$ofTotal$sp"
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
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
        setBusy(false)
        if (error != null && error !is ModelDownloader.CancelledException) {
            Prefs.setLastError(this, "${error.javaClass.simpleName}: ${error.message}")
        }
        if (error is ModelDownloader.CancelledException) toast("Stopped. Tap Resume to carry on.")
        refresh()
    }

    private fun startDownload() {
        val token = tokenEdit.text.toString().trim()
        if (token.isEmpty()) {
            toast("Paste your Hugging Face token first")
            return
        }
        val dest = LlmEngine.modelFile(this)
        if (filesDir.usableSpace < 3L * 1024 * 1024 * 1024 && !ModelDownloader.partFile(dest).exists()) {
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
                phase = ""
                err = t
            }
            ui.post { endTransfer(err) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_MODEL && resultCode == RESULT_OK && uri != null) importModel(uri)
    }

    private fun importModel(uri: Uri) {
        Prefs.setLastError(this, "")
        beginTransfer("Copying…")
        io.execute {
            var err: Throwable? = null
            try {
                LlmEngine.reset()
                val dest = LlmEngine.modelFile(this)
                val size = ModelDownloader.importFrom(this, uri, dest, { d, t -> onProgress(d, t) }) { cancelFlag }
                Prefs.setModelSize(this, size)
                verifyInline(dest, tokenEdit.text.toString().trim().ifEmpty { Prefs.token(this) })
            } catch (t: Throwable) {
                phase = ""
                err = t
            }
            ui.post { endTransfer(err) }
        }
    }

    /** Runs on the io thread. Records Verified / Damaged for the current file. */
    private fun verifyInline(file: File, token: String) {
        phase = "Checking: "
        ui.post { progressBar.progress = 0; progressText.text = "Checking the file is intact\u2026" }
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

    private fun runVerify() {
        val f = LlmEngine.modelFile(this)
        if (!f.exists()) return
        val token = tokenEdit.text.toString().trim().ifEmpty { Prefs.token(this) }
        Prefs.setLastError(this, "")
        beginTransfer("Checking the file is intact\u2026")
        io.execute {
            var err: Throwable? = null
            try {
                verifyInline(f, token)
            } catch (t: Throwable) {
                phase = ""
                err = t
            }
            ui.post { endTransfer(err) }
        }
    }

    private fun removeModel() {
        LlmEngine.reset()
        val f: File = LlmEngine.modelFile(this)
        f.delete()
        ModelDownloader.partFile(f).delete()
        Prefs.setModelSize(this, 0L)
        refresh()
    }

    private fun tryTranslate() {
        val text = tryInput.text.toString().trim()
        if (text.isEmpty()) {
            toast("Type something first")
            return
        }
        if (!LlmEngine.modelExists(this)) {
            toast("Install the model first")
            return
        }
        tryOutput.text = "Translating… the first one takes longer while the model loads."
        setBusy(true)
        io.execute {
            val t0 = System.nanoTime()
            var out: String? = null
            var err: Throwable? = null
            try {
                out = LlmEngine.translate(this, text)
            } catch (t: Throwable) {
                err = t
                LlmEngine.saveError(this, t)
            }
            val secs = (System.nanoTime() - t0) / 1e9
            val shown = if (err != null) "Failed. See the error below." else out.orEmpty() + "\n\n" + String.format("(%.1f s)", secs)
            ui.post {
                tryOutput.text = shown
                setBusy(false)
                refresh()
            }
        }
    }

    companion object {
        private const val REQ_MODEL = 1
    }
}
