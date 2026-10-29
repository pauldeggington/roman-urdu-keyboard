package com.paul.urdukb.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.paul.urdukb.BackgroundDownload
import com.paul.urdukb.BuildConfig
import com.paul.urdukb.LlmEngine
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.ModelDownloader
import com.paul.urdukb.Prefs
import com.paul.urdukb.R
import com.paul.urdukb.SetupActivity
import com.paul.urdukb.SetupActivity.Step
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Everything the first-run guide does apart from drawing it: which step is showing, the model download and file
 * check, whether the keyboard is on yet, and what the buttons say. [SetupScreen] only reads and calls this.
 */
class SetupModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication()

    /** Fires when the guide is finished or closed: true to open the app screen next. The activity collects it. */
    val events = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)


    enum class Tone { NORMAL, OK, ERROR }
    enum class Secondary { NONE, CANCEL_DOWNLOAD, IMPORT_FILE }

    /** The live state, rebuilt a bit more than once a second by [tick]. */
    class Live(
        val chip: String = "",
        val keyboardOn: Boolean = false,
        val keyboardCurrent: Boolean = false,
        val modelLine: String = "",
        val modelTone: Tone = Tone.NORMAL,
        val modelBar: Float? = null,
        val modelNote: String = "",
        val modelPrimary: String = "",
        val secondary: Secondary = Secondary.NONE,
        val secondaryLabel: String = "",
        val practiceText: String = "",
        val practiceDone: Boolean = false,
        val modelLocked: Boolean = false,
        val modelNeedsToken: Boolean = false,
        val metered: Boolean = false,
    )

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    var step by mutableStateOf(Step.WELCOME)
        private set
    var live by mutableStateOf(Live())
        private set
    /** Set when a step hits a problem: the details, so the person can copy them and carry on. */
    var problem by mutableStateOf<String?>(null)
        private set
    var token by mutableStateOf(Prefs.token(ctx))

    @Volatile private var verifying = false
    private var verifyDone = 0L
    private var verifyTotal = 0L
    private var lastBytes = 0L
    private var lastT = 0L
    private var speed = 0.0
    private var practiceStartCount = 0L
    private var autoAdvancePosted = false
    private var polling = false

    private val poller = object : Runnable {
        override fun run() {
            tick()
            ui.postDelayed(this, 700)
        }
    }

    init {
        step = runCatching { Step.valueOf(sp().getString(PREF_STEP, Step.WELCOME.name)!!) }.getOrDefault(Step.WELCOME)
        // emulator preview builds can open any step, to take screenshots
        if (BuildConfig.PREVIEW) {
            saved.get<String>("step")?.let { n -> runCatching { Step.valueOf(n) }.getOrNull()?.let { step = it } }
        }
        if (step == Step.PRACTICE) practiceStartCount = Prefs.count(ctx)
        tick()
    }

    private fun sp() = Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun str(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    private fun gb(b: Long) = String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    private fun eta(sec: Double): String = when {
        sec < 90 -> str(R.string.setup_eta_minute)
        sec < 3600 -> str(R.string.setup_eta_minutes, (sec / 60).toInt() + 1)
        else -> str(R.string.setup_eta_hours, String.format(Locale.US, "%.1f", sec / 3600))
    }

    // ---- lifecycle

    override fun onCleared() { ui.removeCallbacksAndMessages(null) }

    fun start() { ui.removeCallbacks(poller); ui.post(poller) }
    fun stop() { ui.removeCallbacks(poller) }

    // ---- navigation

    fun show(s: Step) {
        if (problem != null) { problem = null; start() }
        step = s
        sp().edit { putString(PREF_STEP, s.name) }
        autoAdvancePosted = false
        if (s == Step.PRACTICE) practiceStartCount = Prefs.count(ctx)
        tick()
    }

    fun next() = show(Step.entries[(step.ordinal + 1).coerceAtMost(Step.entries.lastIndex)])

    /** True when it went back a step; false when the guide should close instead. */
    fun back(): Boolean {
        if (step.ordinal == 0) return false
        show(Step.entries[step.ordinal - 1])
        return true
    }

    fun finish(openApp: Boolean) {
        sp().edit { putBoolean(PREF_DONE, true); remove(PREF_STEP) }
        events.tryEmit(openApp)
    }

    // ---- actions the buttons call

    fun openKeyboardSettings() = ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun showPicker() { ctx.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }
    fun openUrl(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    var wifiOnly: Boolean
        get() = sp().getBoolean(PREF_WIFI_ONLY, true)
        set(v) { sp().edit { putBoolean(PREF_WIFI_ONLY, v) } }

    fun chooseModel(m: ModelChoice) {
        if (m == Prefs.modelChoice(ctx)) return
        if (BackgroundDownload.state(ctx).active || verifying) { toast(str(R.string.setup_cancel_first)); return }
        Prefs.setModelChoice(ctx, m)
        LlmEngine.reset()
        tick()
    }

    fun saveToken(t: String) {
        token = t
        Prefs.setToken(ctx, t)
    }

    fun onModelPrimary() {
        val s = BackgroundDownload.state(ctx)
        when {
            LlmEngine.modelExists(ctx) || s.active || verifying -> next()
            else -> startDownload()
        }
    }

    private fun startDownload() {
        val dest = LlmEngine.modelFile(ctx)
        dest.parentFile?.mkdirs()
        val m = Prefs.modelChoice(ctx)
        if (m.needsToken && Prefs.token(ctx).length < 8) {
            toast(str(R.string.setup_need_token))
            return
        }
        val free = runCatching { StatFs(dest.parentFile!!.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < m.approxBytes + 400_000_000L) {
            toast(str(R.string.setup_need_space, String.format(Locale.US, "%.1f", (m.approxBytes + 400_000_000L) / 1e9)))
            return
        }
        val wifi = BackgroundDownload.isMetered(ctx) && wifiOnly
        BackgroundDownload.start(ctx, allowMobileData = !wifi)
        lastT = 0L; speed = 0.0
        toast(str(R.string.setup_downloading_toast))
        tick()
    }

    fun cancelDownload() { BackgroundDownload.cancel(ctx); tick() }

    fun importFile(uri: Uri) {
        verifying = true
        verifyDone = 0; verifyTotal = 0
        io.execute {
            try {
                LlmEngine.reset()
                val dest = LlmEngine.modelFile(ctx)
                val size = ModelDownloader.importFrom(ctx, uri, dest, { d, t -> verifyDone = d; verifyTotal = t }) { false }
                Prefs.setModelSize(ctx, size)
                verifyFile(dest)
            } catch (t: Throwable) {
                Prefs.setLastError(ctx, "${t.javaClass.simpleName}: ${t.message}")
                ui.post { toast(str(R.string.setup_copy_failed)) }
            } finally {
                verifying = false
            }
        }
    }

    private fun verifyFile(f: File) {
        verifying = true
        verifyDone = 0; verifyTotal = f.length()
        val verdict = ModelDownloader.verify(f, Prefs.url(ctx), Prefs.token(ctx), { d, t -> verifyDone = d; verifyTotal = t }) { false }
        val stamp = Prefs.stamp(f)
        when (verdict) {
            is ModelDownloader.Verdict.Ok -> { Prefs.setVerifiedStamp(ctx, stamp); Prefs.setDamagedStamp(ctx, "") }
            is ModelDownloader.Verdict.Damaged -> {
                Prefs.setVerifiedStamp(ctx, ""); Prefs.setDamagedStamp(ctx, stamp)
                Prefs.setLastError(ctx, str(R.string.verify_damaged, verdict.detail))
            }
            is ModelDownloader.Verdict.Unknown -> Unit // could not check (offline); the file is still usable
        }
        verifying = false
    }

    fun copyProblem() {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("error", problem.orEmpty()))
        toast(str(R.string.sheet_copied))
    }

    fun skipProblem() { problem = null; next() }

    // ---- live state

    fun keyboardEnabled() = ctx.getSystemService(InputMethodManager::class.java)
        ?.enabledInputMethodList?.any { it.packageName == ctx.packageName } == true

    fun keyboardCurrent() = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.startsWith("${ctx.packageName}/") == true

    fun tick() {
        try { tickInner() } catch (t: Throwable) {
            ui.removeCallbacks(poller)
            val detail = "${t.javaClass.simpleName}: ${t.message}\n" + t.stackTrace.take(6).joinToString("\n") { "  at $it" }
            Prefs.setLastError(ctx, "Setup, step ${step.name}: $detail")
            problem = detail
        }
    }

    private fun tickInner() {
        // finish a download that completed while we were away, then check it
        if (!verifying) {
            val installed = runCatching { BackgroundDownload.finishIfDone(ctx) }.getOrNull()
            if (installed != null) {
                verifying = true
                io.execute { runCatching { verifyFile(installed) }; verifying = false }
            }
        }
        val s = BackgroundDownload.state(ctx)
        val ready = LlmEngine.modelExists(ctx) && !verifying
        val pct = if (s.total > 0) (s.done * 100 / s.total).toInt() else 0
        val choice = Prefs.modelChoice(ctx)
        val on = keyboardEnabled()
        val current = keyboardCurrent()

        val chip = if (step.ordinal > Step.MODEL.ordinal && step != Step.DONE && (s.active || verifying)) when {
            verifying -> str(R.string.setup_chip_checking)
            s.paused -> str(R.string.setup_chip_paused, BackgroundDownload.reasonText(s), pct)
            s.active -> str(R.string.setup_chip_downloading, pct)
            else -> ""
        } else ""

        var line = ""; var tone = Tone.NORMAL; var bar: Float? = null; var note = ""; var primary = ""
        var secondary = Secondary.NONE; var secondaryLabel = ""
        when {
            verifying -> {
                line = str(R.string.verify_checking)
                bar = if (verifyTotal > 0) verifyDone.toFloat() / verifyTotal else 0f
                note = str(R.string.setup_almost); primary = str(R.string.next)
            }
            ready -> {
                line = str(R.string.setup_ready); tone = Tone.OK
                note = "${choice.label} · ${gb(LlmEngine.modelFile(ctx).length())}"; primary = str(R.string.next)
            }
            s.active -> {
                updateSpeed(s.done)
                line = if (s.paused) BackgroundDownload.reasonText(s) else str(R.string.setup_downloading)
                bar = if (s.total > 0) s.done.toFloat() / s.total else 0f
                val left = if (speed > 0 && s.total > 0) "  ·  " + str(R.string.setup_left, eta((s.total - s.done) / speed)) else ""
                note = gb(s.done) + (if (s.total > 0) " " + str(R.string.model_of_total, gb(s.total)) else "") +
                    (if (speed > 0) "  ·  " + String.format(Locale.US, "%.1f MB/s", speed / 1048576.0) else "") + left +
                    "\n" + str(R.string.setup_keeps_going)
                primary = str(R.string.next); secondary = Secondary.CANCEL_DOWNLOAD; secondaryLabel = str(R.string.setup_cancel_download)
            }
            s.status == DownloadManager.STATUS_FAILED -> {
                line = BackgroundDownload.reasonText(s); tone = Tone.ERROR
                note = str(R.string.setup_restart_note, choice.sizeText)
                primary = str(R.string.setup_try_again); secondary = Secondary.IMPORT_FILE; secondaryLabel = str(R.string.setup_have_file)
            }
            else -> {
                line = str(R.string.setup_one_time, choice.sizeText)
                note = if (choice == ModelChoice.GEMMA3) str(R.string.setup_takes_few) else str(R.string.setup_takes_long)
                primary = str(R.string.setup_download_now); secondary = Secondary.IMPORT_FILE; secondaryLabel = str(R.string.setup_have_file)
            }
        }

        val translated = Prefs.count(ctx) > practiceStartCount
        val practice = when {
            translated -> str(R.string.setup_practice_done)
            !current -> str(R.string.setup_practice_not_active)
            !ready && s.active -> str(R.string.setup_practice_downloading, pct)
            !ready -> str(R.string.setup_practice_no_model)
            else -> str(R.string.setup_practice_wait)
        }

        live = Live(
            chip = chip, keyboardOn = on, keyboardCurrent = current, modelLine = line, modelTone = tone, modelBar = bar, modelNote = note,
            modelPrimary = primary, secondary = secondary, secondaryLabel = secondaryLabel, practiceText = practice, practiceDone = translated,
            modelLocked = s.active || verifying, modelNeedsToken = choice.needsToken && !LlmEngine.modelExists(ctx),
            metered = BackgroundDownload.isMetered(ctx) && !LlmEngine.modelExists(ctx),
        )

        // the keyboard steps tick themselves off, then move on
        val done = when (step) { Step.ENABLE -> on; Step.SWITCH -> current; else -> false }
        if (done && !autoAdvancePosted) {
            autoAdvancePosted = true
            val at = step
            ui.postDelayed({ if (step == at) next() }, 900)
        }
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

    private companion object {
        const val PREF_STEP = SetupActivity.PREF_STEP
        const val PREF_DONE = SetupActivity.PREF_DONE
        const val PREF_WIFI_ONLY = SetupActivity.PREF_WIFI_ONLY
    }
}
