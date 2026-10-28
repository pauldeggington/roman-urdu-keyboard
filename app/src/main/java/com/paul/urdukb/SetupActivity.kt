package com.paul.urdukb

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.paul.urdukb.ime.Kb
import java.util.concurrent.Executors

/**
 * First-run setup: one task per screen, a progress bar, buttons that jump straight to the right
 * Android setting, and steps that tick themselves off when the person comes back.
 *
 *   Welcome -> Download the translator -> Turn on the keyboard -> Switch to it
 *   -> Make it yours -> Practice -> Done
 *
 * The model downloads in the background (Android's download manager), so the keyboard steps can be
 * done while it runs.
 */
class SetupActivity : Activity() {

    enum class Step { WELCOME, MODEL, ENABLE, SWITCH, PERSONAL, PRACTICE, DONE }

    private lateinit var pal: Palette
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var progressRow: LinearLayout
    private lateinit var backBtn: TextView
    private lateinit var skipBtn: TextView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var primary: Button
    private lateinit var secondary: TextView
    private lateinit var chip: TextView

    private var step = Step.WELCOME
    @Volatile private var verifying = false
    private var verifyDone = 0L
    private var verifyTotal = 0L
    private var lastBytes = 0L
    private var lastT = 0L
    private var speed = 0.0
    private var practiceStartCount = 0L
    private var autoAdvancePosted = false

    // model step views, refreshed by the poller
    private var modelBar: ProgressBar? = null
    private var modelLine: TextView? = null
    private var modelNote: TextView? = null
    private var practiceStatus: TextView? = null
    private var stepDoneMark: View? = null

    private val poller = object : Runnable {
        override fun run() {
            tick()
            ui.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette(this)
        window.statusBarColor = pal.appBg
        window.navigationBarColor = pal.appBg
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.decorView.windowInsetsController?.setSystemBarsAppearance(if (pal.dark) 0 else light, light)
        step = runCatching { Step.valueOf(sp().getString(PREF_STEP, Step.WELCOME.name)!!) }.getOrDefault(Step.WELCOME)
        // emulator preview builds can open any step, to take screenshots
        if (com.paul.urdukb.BuildConfig.PREVIEW) {
            intent.getStringExtra("step")?.let { n -> runCatching { Step.valueOf(n) }.getOrNull()?.let { step = it } }
        }
        setContentView(buildShell())
        show(step, animate = false)
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(poller)
        ui.post(poller)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(poller)
    }

    // the keyboard picker is a dialog: when it closes, focus comes back here
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) tick()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step.ordinal > 0) show(Step.entries[step.ordinal - 1]) else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ================================================================ shell

    private fun buildShell(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.appBg)
        }
        // top bar: back, progress segments, skip
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(4f))
        }
        backBtn = textButton("‹") { onBackPressedCompat() }.apply {
            textSize = 30f
            setPadding(dpi(14f), 0, dpi(14f), dpi(4f))
            contentDescription = "Back"
        }
        top.addView(backBtn)
        progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(progressRow, LinearLayout.LayoutParams(0, dpi(6f), 1f))
        skipBtn = textButton("Skip") { finishSetup(openApp = true) }.apply { setPadding(dpi(16f), dpi(10f), dpi(12f), dpi(10f)) }
        top.addView(skipBtn)
        root.addView(top)

        // download status chip, shown on the later steps while the model downloads
        chip = TextView(this).apply {
            textSize = 12.5f
            setTextColor(pal.onTonal)
            background = roundedBg(pal.tonal, dpf(100f))
            setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f))
            visibility = View.GONE
            setOnClickListener { show(Step.MODEL) }
        }
        root.addView(FrameLayout(this).apply {
            setPadding(dpi(24f), dpi(4f), dpi(24f), 0)
            addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(12f), dpi(24f), dpi(24f))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // bottom bar: one big primary action and a quiet secondary one
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(24f), dpi(8f), dpi(24f), dpi(16f))
        }
        primary = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            stateListAnimator = null
            elevation = 0f
            minHeight = dpi(56f)
            minimumHeight = dpi(56f)
            setTextColor(pal.onPrimary)
            background = roundedBg(pal.primary, dpf(100f))
        }
        bottom.addView(primary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(56f)))
        secondary = textButton("") {}.apply {
            gravity = Gravity.CENTER
            setPadding(dpi(16f), dpi(14f), dpi(16f), dpi(6f))
        }
        bottom.addView(secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bottom)

        // edge to edge: keep clear of the status bar, navigation bar and the on-screen keyboard
        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        return root
    }

    private fun paintProgress() {
        progressRow.removeAllViews()
        val n = Step.entries.size
        for (i in 0 until n) {
            val seg = View(this).apply {
                background = roundedBg(if (i <= step.ordinal) pal.primary else pal.tonal, dpf(3f))
            }
            progressRow.addView(seg, LinearLayout.LayoutParams(0, dpi(6f), 1f).apply {
                if (i < n - 1) marginEnd = dpi(4f)
            })
        }
        progressRow.contentDescription = "Step ${step.ordinal + 1} of $n"
        backBtn.visibility = if (step.ordinal > 0 && step != Step.DONE) View.VISIBLE else View.INVISIBLE
        skipBtn.visibility = if (step == Step.DONE) View.INVISIBLE else View.VISIBLE
    }

    // ================================================================ steps

    private fun show(s: Step, animate: Boolean = true) {
        step = s
        sp().edit { putString(PREF_STEP, s.name) }
        autoAdvancePosted = false
        modelBar = null; modelLine = null; modelNote = null; practiceStatus = null; stepDoneMark = null
        paintProgress()
        val build = {
            content.removeAllViews()
            secondary.visibility = View.GONE
            primary.isEnabled = true
            primary.alpha = 1f
            try {
                when (s) {
                    Step.WELCOME -> buildWelcome()
                    Step.MODEL -> buildModel()
                    Step.ENABLE -> buildEnable()
                    Step.SWITCH -> buildSwitch()
                    Step.PERSONAL -> buildPersonal()
                    Step.PRACTICE -> buildPractice()
                    Step.DONE -> buildDone()
                }
            } catch (t: Throwable) {
                showProblem(t)
            }
            scroll.scrollTo(0, 0)
            tick()
        }
        if (!animate) { build(); return }
        content.animate().alpha(0f).translationX(-dpf(24f)).setDuration(120).withEndAction {
            build()
            content.translationX = dpf(24f)
            content.animate().alpha(1f).translationX(0f).setDuration(200).start()
        }.start()
    }

    private fun next() = show(Step.entries[(step.ordinal + 1).coerceAtMost(Step.entries.lastIndex)])

    private fun buildWelcome() {
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.30f)
                }
            }
            clipToOutline = true
        }
        content.addView(logo, LinearLayout.LayoutParams(dpi(96f), dpi(96f)).apply { topMargin = dpi(24f); gravity = Gravity.CENTER_HORIZONTAL })
        content.addView(heading("Roman Urdu Keyboard").apply { gravity = Gravity.CENTER; setPadding(0, dpi(20f), 0, 0) })
        content.addView(body("Type in English, send it in Roman Urdu. Or the other way round.").apply {
            gravity = Gravity.CENTER
            setPadding(0, dpi(8f), 0, dpi(20f))
        })
        content.addView(feature(R.drawable.ic_setup_translate, "Translate as you type", "One tap on the Translate key turns your message into Roman Urdu, casual or formal."))
        content.addView(feature(R.drawable.ic_setup_offline, "Private and offline", "The translator runs on your phone. What you type is never sent anywhere."))
        content.addView(feature(R.drawable.ic_setup_keyboard, "Feels like Gboard", "Same look and layout you know, with a Translate key on top."))
        content.addView(small("Setup takes about 2 minutes. The translator is a one-time download (2.6 GB, or 0.6 GB for the smaller one), best on Wi-Fi.").apply {
            gravity = Gravity.CENTER
            setPadding(0, dpi(20f), 0, 0)
        })
        primary.text = "Get started"
        primary.setOnClickListener { next() }
    }

    private fun buildModel() {
        content.addView(hero(R.drawable.ic_setup_download))
        content.addView(heading("Download the translator"))
        content.addView(body("A small AI model does the translating. It lives on your phone, so it works with no internet and nothing leaves your phone."))
        content.addView(modelChoiceCards())
        val card = card()
        modelLine = TextView(this).apply {
            textSize = 15f
            setTextColor(pal.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        card.addView(modelLine)
        modelBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(pal.primary)
            progressBackgroundTintList = ColorStateList.valueOf(pal.tonal)
            visibility = View.GONE
        }
        card.addView(modelBar, lp().apply { topMargin = dpi(10f) })
        modelNote = small("")
        card.addView(modelNote, lp().apply { topMargin = dpi(6f) })
        content.addView(card)

        // mobile data choice, only when on a metered network
        if (BackgroundDownload.isMetered(this) && !LlmEngine.modelExists(this)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpi(14f), 0, 0)
            }
            row.addView(icon(R.drawable.ic_setup_wifi, pal.textMuted, 22f))
            row.addView(body("You're on mobile data. Wait for Wi-Fi?").apply { setPadding(dpi(10f), 0, 0, 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Switch(this).apply {
                isChecked = sp().getBoolean(PREF_WIFI_ONLY, true)
                setOnCheckedChangeListener { _, v -> sp().edit { putBoolean(PREF_WIFI_ONLY, v) } }
                contentDescription = "Wait for Wi-Fi"
            })
            content.addView(row)
        }
        primary.setOnClickListener { onModelPrimary() }
    }

    /** Two options: the best-quality model, or a smaller and faster one that needs a free Hugging Face token. */
    private fun modelChoiceCards(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(16f), 0, 0) }
        val current = Prefs.modelChoice(this)
        val locked = BackgroundDownload.state(this).active || verifying
        ModelChoice.entries.forEach { m ->
            val selected = m == current
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(if (selected) pal.tonal else pal.card, dpf(20f)).apply {
                    setStroke(dpi(if (selected) 2f else 1f), if (selected) pal.primary else pal.tonal)
                }
                setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(12f))
                isClickable = true
                setOnClickListener {
                    if (m == current) return@setOnClickListener
                    if (locked) { toast("Cancel the download first to change model."); return@setOnClickListener }
                    Prefs.setModelChoice(this@SetupActivity, m)
                    LlmEngine.reset()
                    show(Step.MODEL, animate = false)
                }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(TextView(this).apply {
                text = (if (selected) "●  " else "○  ") + m.tagline
                textSize = 15.5f
                setTextColor(pal.text)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(small(m.sizeText))
            row.addView(top)
            row.addView(small(
                if (m == ModelChoice.GEMMA4) "Gemma 4 E2B. Understands Roman Urdu best. No account needed."
                else "Gemma 3 1B. Downloads fast and answers faster, but translates less accurately. Needs a free Hugging Face token."
            ).apply { setPadding(0, dpi(4f), 0, 0) })
            box.addView(row, lp().apply { topMargin = dpi(10f) })
        }
        if (current.needsToken && !LlmEngine.modelExists(this)) box.addView(tokenHelp(current))
        return box
    }

    private fun tokenHelp(m: ModelChoice): View {
        val card = card()
        card.addView(small("Google keeps Gemma 3 behind a licence click. It takes about a minute, once:"))
        card.addView(numbered(1, "Open the model page, sign in (free) and tap Agree"))
        card.addView(numbered(2, "Make a free read token and paste it below"))
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dpi(12f), 0, 0) }
        fun pillButton(text: String, url: String) = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(pal.onTonal)
            background = roundedBg(pal.tonal, dpf(100f))
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
        }
        r.addView(pillButton("Model page", m.pageUrl), LinearLayout.LayoutParams(0, dpi(44f), 1f).apply { marginEnd = dpi(8f) })
        r.addView(pillButton("Make a token", "https://huggingface.co/settings/tokens"), LinearLayout.LayoutParams(0, dpi(44f), 1f))
        card.addView(r)
        val field = EditText(this).apply {
            hint = "Paste token (hf_…)"
            setTextColor(pal.text)
            setHintTextColor(pal.textMuted)
            textSize = 14f
            background = roundedBg(pal.field, dpf(14f))
            setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.token(this@SetupActivity))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) { Prefs.setToken(this@SetupActivity, s?.toString().orEmpty()) }
            })
        }
        card.addView(field, lp().apply { topMargin = dpi(12f) })
        return card
    }

    private fun onModelPrimary() {
        val s = BackgroundDownload.state(this)
        when {
            LlmEngine.modelExists(this) || s.active || verifying -> next()
            else -> startDownload()
        }
    }

    private fun startDownload() {
        val dest = LlmEngine.modelFile(this)
        dest.parentFile?.mkdirs()
        val m = Prefs.modelChoice(this)
        if (m.needsToken && Prefs.token(this).length < 8) {
            toast("Paste your Hugging Face token first (step 2 above)")
            return
        }
        val free = runCatching { StatFs(dest.parentFile!!.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < m.approxBytes + 400_000_000L) {
            toast("You need about ${String.format(java.util.Locale.US, "%.1f", (m.approxBytes + 400_000_000L) / 1e9)} GB of free space. Free some up and try again.")
            return
        }
        val wifiOnly = BackgroundDownload.isMetered(this) && sp().getBoolean(PREF_WIFI_ONLY, true)
        BackgroundDownload.start(this, allowMobileData = !wifiOnly)
        lastT = 0L; speed = 0.0
        toast("Downloading. You can carry on with setup.")
        tick()
    }

    private fun importFile() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, REQ_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (requestCode != REQ_FILE || resultCode != RESULT_OK) return
        verifying = true
        verifyDone = 0; verifyTotal = 0
        io.execute {
            try {
                LlmEngine.reset()
                val dest = LlmEngine.modelFile(this)
                val size = ModelDownloader.importFrom(this, uri, dest, { d, t -> verifyDone = d; verifyTotal = t }) { false }
                Prefs.setModelSize(this, size)
                verifyFile(dest)
            } catch (t: Throwable) {
                Prefs.setLastError(this, "${t.javaClass.simpleName}: ${t.message}")
                ui.post { toast("Could not copy that file") }
            } finally {
                verifying = false
            }
        }
    }

    private fun verifyFile(f: java.io.File) {
        verifying = true
        verifyDone = 0; verifyTotal = f.length()
        val verdict = ModelDownloader.verify(f, Prefs.url(this), Prefs.token(this), { d, t -> verifyDone = d; verifyTotal = t }) { false }
        val stamp = Prefs.stamp(f)
        when (verdict) {
            is ModelDownloader.Verdict.Ok -> { Prefs.setVerifiedStamp(this, stamp); Prefs.setDamagedStamp(this, "") }
            is ModelDownloader.Verdict.Damaged -> {
                Prefs.setVerifiedStamp(this, ""); Prefs.setDamagedStamp(this, stamp)
                Prefs.setLastError(this, "The model file is damaged. ${verdict.detail}")
            }
            is ModelDownloader.Verdict.Unknown -> Unit // could not check (offline); the file is still usable
        }
        verifying = false
    }

    private fun buildEnable() {
        content.addView(hero(R.drawable.ic_setup_keyboard))
        content.addView(heading("Turn on the keyboard"))
        content.addView(body("Android keeps new keyboards switched off until you turn them on."))
        content.addView(numbered(1, "Tap Open settings below"))
        content.addView(numbered(2, "Turn on Roman Urdu Keyboard"))
        content.addView(numbered(3, "Tap OK on Android's messages, then come back here"))
        content.addView(toggleIllustration())
        val note = card().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dpi(16f) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(icon(R.drawable.ic_setup_lock, pal.primary, 22f))
        row.addView(small("Android warns that any keyboard could collect what you type. That's a standard message for every keyboard. " +
            "This one works offline: your typing stays on your phone.").apply { setPadding(dpi(12f), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        note.addView(row)
        content.addView(note)
        stepDoneMark = doneBanner("Keyboard is on").also { content.addView(it) }
        primary.text = "Open settings"
        primary.setOnClickListener {
            if (keyboardEnabled()) next() else startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun buildSwitch() {
        content.addView(hero(R.drawable.ic_setup_touch))
        content.addView(heading("Make it your keyboard"))
        content.addView(body("Pick Roman Urdu Keyboard in the list that pops up."))
        content.addView(small("Later you can switch keyboards with the keyboard icon at the bottom corner of the screen, or by holding the globe key.").apply {
            setPadding(0, dpi(10f), 0, 0)
        })
        stepDoneMark = doneBanner("Roman Urdu Keyboard is in use").also { content.addView(it) }
        primary.text = "Choose keyboard"
        primary.setOnClickListener {
            if (keyboardCurrent()) next()
            else getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    private fun buildPersonal() {
        content.addView(hero(R.drawable.ic_setup_person))
        content.addView(heading("Make it yours"))
        content.addView(body("So the translation sounds like you."))

        content.addView(label("I am"))
        val who = segments(
            listOf(Speaker.MALE to "Male", Speaker.FEMALE to "Female"),
            Speaker.get(this),
        ) { Speaker.set(this, it) }
        content.addView(who)
        content.addView(small("\"I'm tired\" becomes \"Main thak gaya hoon\" or \"Main thak gayi hoon\".").apply { setPadding(0, dpi(6f), 0, 0) })

        content.addView(label("How you usually talk"))
        val tone = segments(listOf("casual" to "Casual · tum", "formal" to "Formal · aap"),
            if (RomanUrdu.isFormal(this)) "formal" else "casual") { v ->
            Kb.prefs(this).edit { putBoolean(RomanUrdu.PREF_TONE, v == "formal") }
        }
        content.addView(tone)
        content.addView(small("You can switch any time with the Casual / Formal key on the keyboard.").apply { setPadding(0, dpi(6f), 0, 0) })

        content.addView(label("Keyboard theme"))
        content.addView(segments(
            listOf(KeyboardLook.MODE_FOLLOW to "Follow phone", KeyboardLook.MODE_LIGHT to "Light", KeyboardLook.MODE_DARK to "Dark"),
            KeyboardLook.mode(this),
        ) { KeyboardLook.apply(this, KeyboardLook.swatchId(this), it); recreate() })

        content.addView(label("Keyboard colour"))
        val current = KeyboardLook.swatchId(this)
        KeyboardLook.SWATCHES.take(10).chunked(5).forEach { rowSw ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dpi(6f), 0, 0) }
            rowSw.forEach { sw ->
                val selected = sw.id == current
                val dot = TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = if (selected) "✓" else ""
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    contentDescription = sw.name
                    background = (if (sw.id == KeyboardLook.WALLPAPER)
                        android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                            intArrayOf(0xFFC27BA0.toInt(), 0xFF6C7CE8.toInt(), 0xFF2FB5A8.toInt()))
                    else android.graphics.drawable.GradientDrawable().apply { setColor(sw.color) }).apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        if (selected) setStroke(dpi(3f), pal.text)
                    }
                    setOnClickListener {
                        KeyboardLook.apply(this@SetupActivity, sw.id, KeyboardLook.mode(this@SetupActivity))
                        recreate() // the setup screen follows the colour too
                    }
                }
                val col = FrameLayout(this)
                col.addView(dot, FrameLayout.LayoutParams(dpi(44f), dpi(44f), Gravity.CENTER))
                r.addView(col, LinearLayout.LayoutParams(0, dpi(52f), 1f))
            }
            content.addView(r)
        }
        content.addView(small("Wallpaper matches your phone, like Gboard. More colours are in the app.").apply { setPadding(0, dpi(6f), 0, 0) })
        primary.text = "Next"
        primary.setOnClickListener { next() }
    }

    private fun buildPractice() {
        content.addView(heading("Try it"))
        content.addView(body("Type a sentence in English, then tap the Translate key above the letters."))
        val keys = card().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dpi(12f) }
        keys.addView(keyTip(R.drawable.kb_translate, "Translate", "Turns your text into Roman Urdu. Tap again to undo."))
        keys.addView(keyTip(R.drawable.ic_setup_mood, "Casual / Formal", "tum for friends and family, aap for elders and work."))
        keys.addView(keyTip(R.drawable.ic_setup_swap, "Auto", "Works out the direction. Tap to force English → Roman Urdu or back."))
        content.addView(keys)
        val field = EditText(this).apply {
            hint = "e.g. I'm on my way, see you soon"
            setTextColor(pal.text)
            setHintTextColor(pal.textMuted)
            textSize = 17f
            minLines = 3
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = roundedBg(pal.field, dpf(18f))
            setPadding(dpi(16f), dpi(14f), dpi(16f), dpi(14f))
        }
        content.addView(field, lp().apply { topMargin = dpi(16f) })
        practiceStatus = small("").apply { setPadding(0, dpi(8f), 0, 0) }
        content.addView(practiceStatus)
        practiceStartCount = Prefs.count(this)
        field.post {
            field.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
        }
        primary.text = "Next"
        primary.setOnClickListener { next() }
        secondary.text = "Skip for now"
        secondary.visibility = View.VISIBLE
        secondary.setOnClickListener { next() }
    }

    private fun buildDone() {
        content.addView(hero(R.drawable.ic_setup_celebration))
        content.addView(heading("Sab tayyar hai!"))
        content.addView(body("You're all set. Roman Urdu Keyboard works in every app: WhatsApp, Messages, email, anywhere you type."))
        val tips = card().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dpi(16f) }
        tips.addView(keyTip(R.drawable.ic_setup_translate, "Translate", "Tap it after typing. Tap again to get your original back."))
        tips.addView(keyTip(R.drawable.ic_setup_person, "Sounds wrong?", "Open the app to change who you are, the tone, or teach it your own examples."))
        tips.addView(keyTip(R.drawable.ic_setup_palette, "Colours", "Pick a colour or follow your wallpaper in the app."))
        content.addView(tips)
        primary.text = "Finish"
        primary.setOnClickListener { finishSetup(openApp = true) }
    }

    private fun finishSetup(openApp: Boolean) {
        sp().edit { putBoolean(PREF_DONE, true); remove(PREF_STEP) }
        if (openApp) startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    // ================================================================ live state

    private fun keyboardEnabled() = getSystemService(InputMethodManager::class.java)
        ?.enabledInputMethodList?.any { it.packageName == packageName } == true

    private fun keyboardCurrent() = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.startsWith("$packageName/") == true

    /** Instead of crashing: say what happened, let them copy it, and let them carry on. */
    private fun showProblem(t: Throwable) {
        val detail = "${t.javaClass.simpleName}: ${t.message}\n" + t.stackTrace.take(6).joinToString("\n") { "  at $it" }
        Prefs.setLastError(this, "Setup, step ${step.name}: $detail")
        content.removeAllViews()
        content.addView(heading("Something went wrong"))
        content.addView(body("This step hit a problem. You can skip it and carry on; the details are saved under Last error in the app."))
        content.addView(small(detail).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dpi(14f), 0, 0)
        })
        primary.text = "Skip this step"
        primary.setOnClickListener { next() }
        secondary.text = "Copy details"
        secondary.visibility = View.VISIBLE
        secondary.setOnClickListener {
            getSystemService(android.content.ClipboardManager::class.java)
                ?.setPrimaryClip(android.content.ClipData.newPlainText("error", detail))
            toast("Copied")
        }
    }

    private fun tick() {
        try { tickInner() } catch (t: Throwable) {
            ui.removeCallbacks(poller)
            showProblem(t)
        }
    }

    private fun tickInner() {
        // finish a download that completed while we were away, then check it
        if (!verifying) {
            val installed = runCatching { BackgroundDownload.finishIfDone(this) }.getOrNull()
            if (installed != null) {
                verifying = true
                io.execute { runCatching { verifyFile(installed) }; verifying = false }
            }
        }
        val s = BackgroundDownload.state(this)
        val ready = LlmEngine.modelExists(this) && !verifying
        val pct = if (s.total > 0) (s.done * 100 / s.total).toInt() else 0

        // chip on the later steps
        chip.visibility = if (step.ordinal > Step.MODEL.ordinal && step != Step.DONE && (s.active || verifying)) View.VISIBLE else View.GONE
        chip.text = when {
            verifying -> "Translator: checking the file…"
            s.paused -> "Translator: ${BackgroundDownload.reasonText(s)} · $pct%"
            s.active -> "Translator downloading · $pct%"
            else -> ""
        }

        when (step) {
            Step.MODEL -> paintModel(s, ready)
            Step.ENABLE -> autoAdvance(keyboardEnabled())
            Step.SWITCH -> autoAdvance(keyboardCurrent())
            Step.PRACTICE -> paintPractice(s, ready)
            else -> Unit
        }
    }

    private fun autoAdvance(done: Boolean) {
        stepDoneMark?.visibility = if (done) View.VISIBLE else View.GONE
        if (done) primary.text = "Next"
        if (done && !autoAdvancePosted) {
            autoAdvancePosted = true
            val at = step
            ui.postDelayed({ if (step == at) next() }, 900)
        }
    }

    private fun paintModel(s: BackgroundDownload.State, ready: Boolean) {
        val line = modelLine ?: return
        val bar = modelBar ?: return
        val note = modelNote ?: return
        secondary.visibility = View.VISIBLE
        when {
            verifying -> {
                line.text = "Checking the file is intact…"
                bar.visibility = View.VISIBLE
                bar.progress = if (verifyTotal > 0) (verifyDone * 1000 / verifyTotal).toInt() else 0
                note.text = "Almost there."
                primary.text = "Next"
                secondary.visibility = View.GONE
            }
            ready -> {
                line.text = "✓  Translator ready"
                line.setTextColor(pal.ok)
                bar.visibility = View.GONE
                note.text = "${Prefs.modelChoice(this).label} · ${gb(LlmEngine.modelFile(this).length())}"
                primary.text = "Next"
                secondary.visibility = View.GONE
            }
            s.active -> {
                line.setTextColor(pal.text)
                updateSpeed(s.done)
                line.text = if (s.paused) BackgroundDownload.reasonText(s) else "Downloading…"
                bar.visibility = View.VISIBLE
                bar.progress = if (s.total > 0) (s.done * 1000 / s.total).toInt() else 0
                val left = if (speed > 0 && s.total > 0) "  ·  about ${eta((s.total - s.done) / speed)} left" else ""
                note.text = gb(s.done) + (if (s.total > 0) " of ${gb(s.total)}" else "") +
                    (if (speed > 0) "  ·  " + String.format(java.util.Locale.US, "%.1f MB/s", speed / 1048576.0) else "") + left +
                    "\nIt keeps going in the background, so carry on with setup."
                primary.text = "Next"
                secondary.text = "Cancel download"
                secondary.setOnClickListener { BackgroundDownload.cancel(this); tick() }
            }
            s.status == android.app.DownloadManager.STATUS_FAILED -> {
                line.text = BackgroundDownload.reasonText(s)
                line.setTextColor(pal.error)
                bar.visibility = View.GONE
                note.text = "It picks up from the start. About ${Prefs.modelChoice(this).sizeText}."
                primary.text = "Try again"
                secondary.text = "I already have the file"
                secondary.setOnClickListener { importFile() }
            }
            else -> {
                line.setTextColor(pal.text)
                line.text = "One-time download · about ${Prefs.modelChoice(this).sizeText}"
                bar.visibility = View.GONE
                note.text = if (Prefs.modelChoice(this) == ModelChoice.GEMMA3) "Takes a few minutes on Wi-Fi."
                    else "Takes 5 to 15 minutes on Wi-Fi. Needs about 3 GB free."
                primary.text = "Download now"
                secondary.text = "I already have the file"
                secondary.setOnClickListener { importFile() }
            }
        }
    }

    private fun paintPractice(s: BackgroundDownload.State, ready: Boolean) {
        val st = practiceStatus ?: return
        val translated = Prefs.count(this) > practiceStartCount
        st.setTextColor(if (translated) pal.ok else pal.textMuted)
        st.text = when {
            translated -> "✓  Shabash! That's all there is to it."
            !keyboardCurrent() -> "Roman Urdu Keyboard isn't the active keyboard yet. Go back one step to switch."
            !ready && s.active -> "The translator is still downloading (${if (s.total > 0) s.done * 100 / s.total else 0}%). " +
                "Translate starts working as soon as it's done."
            !ready -> "The translator isn't downloaded yet. Go back to the Download step."
            else -> "The first translation takes a few seconds while the translator wakes up."
        }
        if (translated) primary.text = "Next"
    }

    private fun updateSpeed(done: Long) {
        val now = System.nanoTime()
        if (lastT == 0L || done < lastBytes) { lastT = now; lastBytes = done; return }
        val dt = (now - lastT) / 1e9
        if (dt >= 2.0) {
            val inst = (done - lastBytes) / dt
            speed = if (speed == 0.0) inst else speed * 0.6 + inst * 0.4
            lastT = now; lastBytes = done
        }
    }

    // ================================================================ view helpers

    private fun sp() = Prefs.store(this).getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun dpf(v: Float) = v * resources.displayMetrics.density
    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun gb(b: Long) = String.format(java.util.Locale.US, "%.2f GB", b / 1073741824.0)
    private fun eta(sec: Double): String = when {
        sec < 90 -> "1 minute"
        sec < 3600 -> "${(sec / 60).toInt() + 1} minutes"
        else -> String.format(java.util.Locale.US, "%.1f hours", sec / 3600)
    }

    private fun onBackPressedCompat() {
        @Suppress("DEPRECATION")
        onBackPressed()
    }

    private fun textButton(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(pal.primary)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun heading(t: String) = TextView(this).apply {
        text = t
        textSize = 28f
        setTextColor(pal.text)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dpi(16f), 0, 0)
        setLineSpacing(0f, 1.1f)
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 16f
        setTextColor(pal.textMuted)
        setPadding(0, dpi(10f), 0, 0)
        setLineSpacing(0f, 1.2f)
    }

    private fun small(t: String) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(pal.textMuted)
        setLineSpacing(0f, 1.15f)
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(pal.text)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dpi(22f), 0, dpi(8f))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(pal.card, dpf(24f))
        setPadding(dpi(18f), dpi(16f), dpi(18f), dpi(16f))
        layoutParams = lp().apply { topMargin = dpi(20f) }
    }

    private fun icon(res: Int, tint: Int, sizeDp: Float) = ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dpi(sizeDp), dpi(sizeDp))
    }

    private fun hero(res: Int): View = FrameLayout(this).apply {
        background = roundedBg(pal.tonal, dpf(48f))
        val img = ImageView(this@SetupActivity).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(pal.onTonal)
        }
        addView(img, FrameLayout.LayoutParams(dpi(48f), dpi(48f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dpi(96f), dpi(96f)).apply { topMargin = dpi(16f) }
    }

    private fun feature(res: Int, title: String, sub: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpi(10f), 0, dpi(10f))
        }
        val bubble = FrameLayout(this).apply {
            background = roundedBg(pal.tonal, dpf(22f))
            addView(icon(res, pal.onTonal, 24f), FrameLayout.LayoutParams(dpi(24f), dpi(24f), Gravity.CENTER))
        }
        row.addView(bubble, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(14f), 0, 0, 0) }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(pal.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        texts.addView(small(sub).apply { setPadding(0, dpi(2f), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun keyTip(res: Int, title: String, sub: String): View = feature(res, title, sub)

    private fun numbered(n: Int, t: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(14f), 0, 0)
        }
        row.addView(TextView(this).apply {
            text = n.toString()
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pal.onPrimary)
            background = roundedBg(pal.primary, dpf(15f))
        }, LinearLayout.LayoutParams(dpi(30f), dpi(30f)))
        row.addView(TextView(this).apply {
            text = t
            textSize = 16f
            setTextColor(pal.text)
            setPadding(dpi(14f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun doneBanner(t: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundedBg(pal.tonal, dpf(100f))
        setPadding(dpi(14f), dpi(10f), dpi(18f), dpi(10f))
        addView(icon(R.drawable.ic_setup_check, pal.ok, 22f))
        addView(TextView(this@SetupActivity).apply {
            text = t
            textSize = 15f
            setTextColor(pal.text)
            setPadding(dpi(10f), 0, 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(18f) }
        visibility = View.GONE
    }

    /** A little settings row whose switch flips on and off, showing what to tap. */
    private fun toggleIllustration(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(pal.card, dpf(20f))
            setPadding(dpi(18f), dpi(16f), dpi(18f), dpi(16f))
            layoutParams = lp().apply { topMargin = dpi(20f) }
        }
        row.addView(TextView(this).apply {
            text = "Roman Urdu Keyboard"
            textSize = 15f
            setTextColor(pal.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val track = FrameLayout(this).apply { background = roundedBg(pal.tonal, dpf(16f)) }
        val thumb = View(this).apply { background = roundedBg(pal.textMuted, dpf(11f)) }
        track.addView(thumb, FrameLayout.LayoutParams(dpi(22f), dpi(22f), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = dpi(5f) })
        row.addView(track, LinearLayout.LayoutParams(dpi(52f), dpi(32f)))
        val travel = dpf(20f)
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val f = it.animatedValue as Float
                val on = f in 0.35f..0.9f
                val x = when {
                    f < 0.35f -> 0f
                    f < 0.45f -> (f - 0.35f) / 0.1f
                    f < 0.9f -> 1f
                    else -> 1f - (f - 0.9f) / 0.1f
                }
                thumb.translationX = x * travel
                thumb.background = roundedBg(if (on) pal.onPrimary else pal.textMuted, dpf(11f))
                track.background = roundedBg(if (on) pal.primary else pal.tonal, dpf(16f))
            }
            start()
        }
        // stop when the step changes, so it doesn't keep running off screen
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = anim.cancel()
        })
        return row
    }

    /** Pill segmented control. */
    private fun segments(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = mutableListOf<Pair<String, TextView>>()
        fun paint(sel: String) = buttons.forEach { (id, b) ->
            val on = id == sel
            b.setTextColor(if (on) pal.onPrimary else pal.onTonal)
            b.background = roundedBg(if (on) pal.primary else pal.tonal, dpf(100f))
        }
        options.forEachIndexed { i, (id, text) ->
            val b = TextView(this).apply {
                this.text = text
                gravity = Gravity.CENTER
                textSize = 14.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setOnClickListener { onPick(id); paint(id) }
            }
            buttons += id to b
            row.addView(b, LinearLayout.LayoutParams(0, dpi(48f), 1f).apply { if (i < options.lastIndex) marginEnd = dpi(8f) })
        }
        paint(selected)
        return row
    }

    companion object {
        private const val PREF_STEP = "setup_step"
        private const val PREF_DONE = "setup_done"
        private const val PREF_WIFI_ONLY = "setup_wifi_only"
        private const val REQ_FILE = 7

        fun isDone(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(PREF_DONE, false)

        fun markDone(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_DONE, true).apply()

        /** The keyboard changed (an update replaced it), so ask the person to turn the new one on. */
        fun resumeAtEnable(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_DONE, false).putString(PREF_STEP, Step.ENABLE.name).apply()

        fun reset(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_DONE, false).remove(PREF_STEP).apply()
    }
}
