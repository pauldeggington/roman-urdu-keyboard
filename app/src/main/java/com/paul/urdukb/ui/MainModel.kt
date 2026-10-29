package com.paul.urdukb.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.paul.urdukb.BackgroundDownload
import com.paul.urdukb.LlmEngine
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.ModelDownloader
import com.paul.urdukb.Prefs
import com.paul.urdukb.R
import com.paul.urdukb.RomanUrdu
import com.paul.urdukb.Speaker
import com.paul.urdukb.Style
import com.paul.urdukb.Updater
import com.paul.urdukb.Words
import com.paul.urdukb.ime.Kb
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** The parts of the app screen the person can jump to from the checklist or from another screen. */
enum class Section { MODEL, TRY, COLOUR, GIPHY }

/**
 * Everything the main screen shows and does, apart from drawing it: the model download, the file check, the update
 * check, the Try it box and the style editor. The screen ([MainScreen]) only reads these values and calls these
 * functions, so a change to how something looks never touches how it works.
 */
class MainModel(private val act: ComponentActivity) {

    enum class Tone { NORMAL, OK, ERROR }

    class Check(val done: Boolean, val label: String, val sub: String, val action: String?)

    /** What the model, checklist, speed and error cards show; rebuilt by [refresh]. */
    class Snapshot(
        val status: String = "",
        val statusTone: Tone = Tone.NORMAL,
        val detail: String = "",
        val choice: ModelChoice = ModelChoice.GEMMA4,
        val downloadLabel: String = "",
        val downloading: Boolean = false,
        val showRemove: Boolean = false,
        val showVerify: Boolean = false,
        val barProgress: Float? = null,
        val checks: List<Check> = emptyList(),
        val stats: String = "",
        val gpu: Boolean = false,
        val error: String = "",
        val formal: Boolean = false,
        val direction: String = RomanUrdu.DIR_AUTO,
    )

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var snap by mutableStateOf(Snapshot())
        private set
    var busy by mutableStateOf(false)
        private set
    /** While a file is being copied or checked: what to say and how far along it is (0 to 1). */
    var transferText by mutableStateOf<String?>(null)
        private set
    var transferProgress by mutableStateOf(0f)
        private set

    var scrollRequest by mutableStateOf<Section?>(null)
    var focusTryBox by mutableStateOf(0)

    // ---- updates
    var updateText by mutableStateOf("")
        private set
    var updateButton by mutableStateOf("")
        private set
    var updateIsNew by mutableStateOf(false)
        private set
    private var latestRelease: Updater.Release? = null
    private var checking = false

    // ---- model token and downloads
    var token by mutableStateOf(Prefs.token(act))
    @Volatile private var cancelFlag = false
    @Volatile private var phase = ""
    private var lastSpeedT = 0L
    private var lastSpeedBytes = 0L
    private var speed = 0.0
    private var polling = false

    // ---- Try it
    var tryInput by mutableStateOf("")
    var tryOutput by mutableStateOf("")
    var tryHasResult by mutableStateOf(false)
        private set
    var tryNote by mutableStateOf("")
        private set
    private var lastStyle: Style? = null
    private var lastInput = ""
    val canSaveExample: Boolean get() = tryHasResult && lastStyle != null
    private var thinkingNow = false
    private val thinkRunnable = object : Runnable {
        override fun run() {
            if (thinkingNow) {
                tryOutput = Words.thinking()
                main.postDelayed(this, 1600)
            }
        }
    }

    // ---- style editor
    var speaker by mutableStateOf(Speaker.get(act))
        private set
    var styleOpen by mutableStateOf(false)
    var styleSel by mutableStateOf(Style.CASUAL)
        private set
    var rules by mutableStateOf("")
    var examples by mutableStateOf("")
    var glossary by mutableStateOf(Prefs.glossary(act))

    init {
        paintUpdate()
        showStyle(Style.CASUAL)
        checkForUpdate(quiet = true)
        refresh()
    }

    private fun toast(s: String) = Toast.makeText(act, s, Toast.LENGTH_SHORT).show()
    private fun str(id: Int, vararg a: Any) = act.getString(id, *a)
    private fun gb(bytes: Long) = String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
    private fun secs(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000.0)

    // ================================================================ updates

    private fun paintUpdate() {
        val rel = latestRelease
        if (rel != null && Updater.isNewer(act, rel)) {
            val firstChange = rel.notes.substringAfter("## Changes", "").trim().lines().firstOrNull { it.isNotBlank() }.orEmpty()
            updateText = str(R.string.update_ready, rel.name, Updater.installedName(act)) + "\n" + firstChange + "\n" + str(R.string.update_downloads_in_browser)
            updateButton = str(R.string.update_download)
            updateIsNew = true
        } else {
            updateText = str(R.string.update_version, Updater.installedName(act)) + if (rel != null) str(R.string.update_up_to_date) else ""
            updateButton = str(R.string.update_check)
            updateIsNew = false
        }
    }

    fun checkForUpdate(quiet: Boolean) {
        if (checking) return
        checking = true
        if (!quiet) updateText = str(R.string.update_checking)
        io.execute {
            val r = runCatching { Updater.latest() }
            main.post {
                checking = false
                r.onSuccess { rel ->
                    latestRelease = rel
                    paintUpdate()
                    if (!quiet && !Updater.isNewer(act, rel)) toast(str(R.string.update_latest))
                }.onFailure { e ->
                    if (!quiet) updateText = str(R.string.update_failed, Updater.installedName(act), e.message.orEmpty())
                }
            }
        }
    }

    fun onUpdateClick() {
        val rel = latestRelease
        if (rel == null || !Updater.isNewer(act, rel)) { checkForUpdate(quiet = false); return }
        // the browser downloads the APK; Android then asks to install it, like any downloaded app
        runCatching { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl))) }
            .onFailure { toast(str(R.string.update_browser_failed, Updater.REPO)) }
    }

    fun onResume() {
        refresh()
        paintUpdate()
    }

    // ================================================================ state of the model, keyboard and speed

    fun refresh() {
        if (!busy) BackgroundDownload.finishIfDone(act)?.let { runVerify() }
        val bg = BackgroundDownload.state(act)
        val f = LlmEngine.modelFile(act)
        val part = ModelDownloader.partFile(f)
        val ok = LlmEngine.modelExists(act)
        val expected = Prefs.modelSize(act)
        val verified = f.exists() && Prefs.verifiedStamp(act) == Prefs.stamp(f)
        val damaged = f.exists() && Prefs.damagedStamp(act) == Prefs.stamp(f)
        val choice = Prefs.modelChoice(act)

        var status: String
        var tone = Tone.NORMAL
        var detail: String
        when {
            damaged -> {
                status = str(R.string.model_damaged, gb(f.length())); tone = Tone.ERROR
                detail = str(R.string.model_damaged_detail)
            }
            ok -> {
                status = str(if (verified) R.string.model_ready_verified else R.string.model_ready, gb(f.length())); tone = Tone.OK
                detail = if (verified) str(R.string.model_checksum_ok, choice.label) else str(R.string.model_not_checked)
            }
            f.exists() -> {
                status = str(R.string.model_incomplete); tone = Tone.ERROR
                detail = str(R.string.model_incomplete_detail, gb(f.length())) +
                    (if (expected > 0) str(R.string.model_expected, gb(expected)) else ".") + " " + str(R.string.model_remove_redownload)
            }
            part.exists() && part.length() > 0 -> {
                status = str(R.string.model_partial, gb(part.length()))
                detail = str(R.string.model_resume_hint)
            }
            else -> {
                status = str(R.string.model_none)
                detail = str(R.string.model_none_detail, choice.label, choice.sizeText)
            }
        }
        var bar: Float? = null
        if (bg.active) {
            val pct = if (bg.total > 0) bg.done * 100 / bg.total else 0
            status = (if (bg.paused) BackgroundDownload.reasonText(bg) else str(R.string.model_downloading)) + "  •  $pct%"
            tone = Tone.NORMAL
            detail = gb(bg.done) + (if (bg.total > 0) " " + str(R.string.model_of_total, gb(bg.total)) else "") + ". " + str(R.string.model_can_leave)
            bar = if (bg.total > 0) bg.done.toFloat() / bg.total else 0f
            if (!polling) {
                polling = true
                main.postDelayed({ polling = false; if (!act.isFinishing) refresh() }, 1500)
            }
        }
        if (bg.status == DownloadManager.STATUS_FAILED) {
            status = BackgroundDownload.reasonText(bg)
            tone = Tone.ERROR
        }
        val downloadLabel = when {
            !ok && part.exists() && part.length() > 0 -> str(R.string.model_resume_download)
            ok -> str(R.string.model_download_again)
            else -> str(R.string.model_download)
        }

        // keyboard state for the checklist
        val imm = act.getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == act.packageName } == true
        val current = Settings.Secure.getString(act.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("${act.packageName}/") == true
        val tried = Prefs.count(act) > 0
        val checks = listOf(
            Check(ok && verified, str(R.string.check_model), if (ok && !verified) str(R.string.check_model_unchecked) else str(R.string.check_model_sub, choice.label, choice.sizeText), str(R.string.check_get)),
            Check(enabled, str(R.string.check_turn_on), str(R.string.check_turn_on_sub), str(R.string.check_turn_on_action)),
            Check(current, str(R.string.check_switch), str(R.string.check_switch_sub), str(R.string.check_switch_action)),
            Check(tried, str(R.string.check_translate), str(R.string.check_translate_sub), str(R.string.check_try)),
        )

        // speed numbers
        val n = Prefs.count(act)
        val backend = Prefs.backendUsed(act)
        val none = str(R.string.stats_none)
        val running = when {
            backend.isEmpty() -> str(R.string.stats_not_loaded)
            Prefs.gpu(act) && backend == "CPU" -> str(R.string.stats_gpu_failed)
            else -> backend
        }
        val over = if (n == 1L) str(R.string.stats_over_one) else str(R.string.stats_over_many, n)
        val stats = listOf(
            str(R.string.stats_file, if (f.exists()) gb(f.length()) else str(R.string.stats_no_file)),
            str(R.string.stats_running, running),
            str(R.string.stats_load, if (Prefs.loadMs(act) > 0) secs(Prefs.loadMs(act)) else none),
            str(R.string.stats_last, if (n > 0) secs(Prefs.lastMs(act)) else none),
            str(R.string.stats_average, if (n > 0) secs(Prefs.totalMs(act) / n) else none, over),
        ).joinToString("\n")

        snap = Snapshot(
            status = status, statusTone = tone, detail = detail, choice = choice, downloadLabel = downloadLabel,
            downloading = bg.active, showRemove = f.exists() || part.exists(), showVerify = f.exists() && !verified,
            barProgress = bar, checks = checks, stats = stats, gpu = Prefs.gpu(act), error = Prefs.lastError(act),
            formal = RomanUrdu.isFormal(act), direction = RomanUrdu.direction(act),
        )
        updateBusy(busy)
    }

    private fun updateBusy(b: Boolean) {
        busy = b
        if (b) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ================================================================ checklist actions

    fun openKeyboardSettings() = act.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    fun showPicker() { act.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }

    fun onCheckAction(index: Int) {
        when (index) {
            0 -> scrollRequest = Section.MODEL
            1 -> openKeyboardSettings()
            2 -> showPicker()
            3 -> { scrollRequest = Section.TRY; focusTryBox++ }
        }
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
        main.post {
            if (total > 0) transferProgress = done.toFloat() / total
            val ofTotal = if (total > 0) " " + str(R.string.model_of_total, gb(total)) else ""
            val sp = if (speed > 0) "  •  " + String.format(Locale.US, "%.1f MB/s", speed / 1048576.0) else ""
            transferText = label + gb(done) + ofTotal + sp
        }
    }

    private fun beginTransfer(message: String) {
        cancelFlag = false
        lastSpeedT = 0L
        speed = 0.0
        transferProgress = 0f
        transferText = message
        updateBusy(true)
    }

    private fun endTransfer(error: Throwable?) {
        phase = ""
        transferText = null
        updateBusy(false)
        if (error is ModelDownloader.CancelledException) toast(str(R.string.transfer_stopped))
        else if (error != null) Prefs.setLastError(act, "${error.javaClass.simpleName}: ${error.message}")
        refresh()
    }

    /** Runs on the io thread. Records Verified / Damaged for the current file. */
    private fun verifyInline(file: File, token: String) {
        phase = str(R.string.verify_phase) + " "
        lastSpeedT = 0L
        speed = 0.0
        main.post { transferProgress = 0f; transferText = str(R.string.verify_checking) }
        val verdict = ModelDownloader.verify(file, Prefs.url(act), token, { d, t -> onProgress(d, t) }) { cancelFlag }
        phase = ""
        val stamp = Prefs.stamp(file)
        when (verdict) {
            is ModelDownloader.Verdict.Ok -> {
                Prefs.setVerifiedStamp(act, stamp)
                Prefs.setDamagedStamp(act, "")
            }
            is ModelDownloader.Verdict.Damaged -> {
                Prefs.setVerifiedStamp(act, "")
                Prefs.setDamagedStamp(act, stamp)
                Prefs.setLastError(act, str(R.string.verify_damaged, verdict.detail))
            }
            is ModelDownloader.Verdict.Unknown -> {
                Prefs.setVerifiedStamp(act, "")
                Prefs.setDamagedStamp(act, "")
                main.post { toast(str(R.string.verify_unknown, verdict.reason)) }
            }
        }
    }

    private fun currentToken() = token.trim().ifEmpty { Prefs.token(act) }

    fun chooseModel(m: ModelChoice) {
        if (m == Prefs.modelChoice(act)) return
        if (BackgroundDownload.state(act).active) BackgroundDownload.cancel(act)
        Prefs.setModelChoice(act, m)
        LlmEngine.reset()
        refresh()
    }

    fun startDownload() {
        val t = token.trim()
        val m = Prefs.modelChoice(act)
        val dest = LlmEngine.modelFile(act)
        dest.parentFile?.mkdirs()
        if (m.needsToken && t.length < 8) {
            toast(str(R.string.download_need_token))
            return
        }
        if (dest.parentFile!!.usableSpace < m.approxBytes + 400_000_000L) {
            toast(str(R.string.download_need_space, String.format(Locale.US, "%.1f", (m.approxBytes + 400_000_000L) / 1e9)))
            return
        }
        Prefs.setToken(act, t)
        if (dest.exists()) removeModel()
        BackgroundDownload.start(act, allowMobileData = true)
        toast(str(R.string.download_started))
        refresh()
    }

    fun cancelDownload() {
        cancelFlag = true
        if (BackgroundDownload.state(act).active) { BackgroundDownload.cancel(act); refresh() }
    }

    fun runVerify() {
        val f = LlmEngine.modelFile(act)
        if (!f.exists()) return
        val t = currentToken()
        Prefs.setLastError(act, "")
        beginTransfer(str(R.string.verify_checking))
        io.execute {
            var err: Throwable? = null
            try {
                verifyInline(f, t)
            } catch (e: Throwable) {
                err = e
            }
            main.post { endTransfer(err) }
        }
    }

    fun importModel(uri: Uri) {
        Prefs.setLastError(act, "")
        val t = currentToken()
        beginTransfer(str(R.string.import_copying))
        io.execute {
            var err: Throwable? = null
            try {
                LlmEngine.reset()
                val dest = LlmEngine.modelFile(act)
                val size = ModelDownloader.importFrom(act, uri, dest, { d, tot -> onProgress(d, tot) }) { cancelFlag }
                Prefs.setModelSize(act, size)
                verifyInline(dest, t)
            } catch (e: Throwable) {
                err = e
            }
            main.post { endTransfer(err) }
        }
    }

    fun removeModel() {
        LlmEngine.reset()
        val f = LlmEngine.modelFile(act)
        f.delete()
        ModelDownloader.partFile(f).delete()
        Prefs.setModelSize(act, 0L)
        Prefs.setVerifiedStamp(act, "")
        Prefs.setDamagedStamp(act, "")
        refresh()
    }

    // ================================================================ Try it

    fun setTone(formal: Boolean) {
        Kb.prefs(act).edit { putBoolean(RomanUrdu.PREF_TONE, formal) }
        refresh()
    }

    fun setDirection(dir: String) {
        Kb.prefs(act).edit { putString(RomanUrdu.PREF_DIRECTION, dir) }
        refresh()
    }

    fun copyOutput() {
        val t = tryOutput
        if (t.isBlank()) return
        act.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Translation", t))
        toast(Words.copied())
    }

    fun saveExample() {
        val style = lastStyle
        val out = tryOutput.trim()
        if (style == null || lastInput.isBlank() || out.isBlank()) return
        Prefs.addExample(act, style, lastInput, out)
        toast(str(R.string.example_saved))
        if (styleSel == style) showStyle(style)
    }

    fun tryTranslate() {
        val text = tryInput.trim()
        if (text.isEmpty()) {
            toast(str(R.string.try_type_first))
            return
        }
        if (!LlmEngine.modelExists(act)) {
            toast(str(R.string.try_get_model))
            scrollRequest = Section.MODEL
            return
        }
        val style = RomanUrdu.styleFor(act, text)
        tryHasResult = false
        val direction = when (style) {
            Style.TO_ENGLISH -> str(R.string.note_to_english)
            Style.FORMAL -> str(R.string.note_formal)
            Style.CASUAL -> str(R.string.note_casual)
        }
        val firstLoad = !LlmEngine.isLoaded()
        tryNote = direction + if (firstLoad) str(R.string.note_first_load) else ""
        tryOutput = Words.thinking()
        thinkingNow = true
        main.postDelayed(thinkRunnable, 1600)
        updateBusy(true)
        io.execute {
            var out: String? = null
            var err: Throwable? = null
            try {
                out = LlmEngine.translate(act, text, style)
            } catch (t: Throwable) {
                err = t
                LlmEngine.saveError(act, t)
            }
            val result = out.orEmpty()
            main.post {
                thinkingNow = false
                main.removeCallbacks(thinkRunnable)
                if (err != null) {
                    tryOutput = str(R.string.try_failed)
                } else {
                    tryOutput = result
                    tryHasResult = true
                    lastStyle = style
                    lastInput = text
                    tryNote = direction + "  ·  " + Words.done()
                }
                updateBusy(false)
                refresh()
            }
        }
    }

    // ================================================================ style editor

    fun chooseSpeaker(v: String) {
        Speaker.set(act, v)
        speaker = v
        toast(if (v == Speaker.MALE) "Main kar raha hoon" else "Main kar rahi hoon")
    }

    fun showStyle(style: Style) {
        styleSel = style
        rules = Prefs.rules(act, style)
        examples = Prefs.examples(act, style)
    }

    fun saveStyle() {
        Prefs.setStyle(act, styleSel, rules, examples)
        Prefs.setGlossary(act, glossary)
        toast(str(R.string.saved))
    }

    fun resetStyle() {
        Prefs.resetStyle(act, styleSel)
        showStyle(styleSel)
        toast(str(R.string.style_reset))
    }

    fun resetGlossary() {
        Prefs.resetGlossary(act)
        glossary = Prefs.glossary(act)
        toast(str(R.string.glossary_reset))
    }

    // ================================================================ speed and errors

    fun setGpu(on: Boolean) {
        LlmEngine.setUseGpu(act, on)
        toast(str(R.string.gpu_next_time))
        refresh()
    }

    fun resetStats() {
        Prefs.resetStats(act)
        refresh()
    }

    fun copyError() {
        act.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("error", Prefs.lastError(act)))
        toast(Words.copied())
    }

    fun clearError() {
        Prefs.setLastError(act, "")
        refresh()
    }
}
