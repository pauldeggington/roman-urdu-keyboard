package com.paul.urdukb.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.paul.urdukb.LlmEngine
import com.paul.urdukb.R
import com.paul.urdukb.RomanUrdu
import com.paul.urdukb.voice.AudioDecoder
import com.paul.urdukb.voice.M4a
import com.paul.urdukb.voice.Pcm
import com.paul.urdukb.voice.Recorder
import com.paul.urdukb.voice.UrduSpeech
import com.paul.urdukb.voice.VoicePrompts
import java.io.File
import java.util.concurrent.Executors

/**
 * The two voice features. Urdu voice note in (shared from WhatsApp): the model listens and gives Urdu text and English.
 * English speech in (recorded here): the model writes the English down, then Urdu script and Roman Urdu, and the phone's
 * Urdu voice reads it into an audio file that can be sent. All on the phone.
 */
class VoiceModel(app: Application) : AndroidViewModel(app) {

    enum class Mode { URDU_TO_ENGLISH, ENGLISH_TO_URDU }

    sealed interface UiState {
        data object Idle : UiState
        data object NoModel : UiState
        data object Decoding : UiState
        data class Listening(val part: Int, val of: Int) : UiState
        data object WritingUrdu : UiState
        data object WritingEnglish : UiState
        data class Recording(val level: Float, val seconds: Int) : UiState
        data object TooQuiet : UiState
        data object Failed : UiState
        data class Done(val urdu: String, val english: String, val roman: String, val truncated: Boolean) : UiState
    }

    /** The audio file for English to Urdu: made after the text is shown. */
    sealed interface AudioState {
        data object None : AudioState
        data object Making : AudioState
        data class Ready(val file: File) : AudioState
        data object VoiceMissing : AudioState
        data object Failed : AudioState
    }

    private val ctx: Context get() = getApplication()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var mode by mutableStateOf(Mode.URDU_TO_ENGLISH)
        private set
    var state by mutableStateOf<UiState>(UiState.Idle)
        private set
    var audio by mutableStateOf<AudioState>(AudioState.None)
        private set
    var copied by mutableStateOf("")
        private set
    val firstLoad = !LlmEngine.isLoaded()

    private var recorder: Recorder? = null
    private var speech: UrduSpeech? = null
    private var started = false

    private fun str(id: Int, vararg a: Any) = ctx.getString(id, *a)

    private fun post(block: () -> Unit) { main.post(block) }

    // ---------------------------------------------------------------- Urdu voice note in

    /** Starts on a shared voice note. Only the first call does anything (the screen may be recreated). */
    fun openSharedAudio(uri: Uri) {
        if (started) return
        started = true
        mode = Mode.URDU_TO_ENGLISH
        if (!LlmEngine.modelExists(ctx)) { state = UiState.NoModel; return }
        state = UiState.Decoding
        io.execute {
            try {
                val decoded = AudioDecoder.decode(ctx, uri)
                if (Pcm.isSilent(decoded.samples)) { post { state = UiState.TooQuiet }; return@execute }
                val pieces = Pcm.split(decoded.samples)
                // step one: only write down what is said
                val parts = ArrayList<String>()
                pieces.forEachIndexed { i, piece ->
                    post { state = UiState.Listening(i + 1, pieces.size) }
                    parts += VoicePrompts.cleanTranscript(LlmEngine.listen(ctx, piece, VoicePrompts.TRANSCRIBE_URDU))
                }
                val urdu = VoicePrompts.join(parts)
                if (urdu.isEmpty()) { post { state = UiState.TooQuiet }; return@execute }
                // step two: the transcript is translated by the text model
                post { state = UiState.WritingEnglish }
                val english = LlmEngine.askText(ctx, VoicePrompts.URDU_TEXT_TO_ENGLISH_RULES, urdu)
                post { state = UiState.Done(urdu, english, "", decoded.truncated) }
            } catch (t: Throwable) {
                LlmEngine.saveError(ctx, t)
                post { state = UiState.Failed }
            }
        }
    }

    // ---------------------------------------------------------------- English speech in

    fun prepareSpeak() {
        if (started) return
        started = true
        mode = Mode.ENGLISH_TO_URDU
        if (!LlmEngine.modelExists(ctx)) state = UiState.NoModel
    }

    val isRecording: Boolean get() = state is UiState.Recording

    /** The record button: starts recording, or stops it and translates. The caller has checked the microphone permission. */
    fun toggleRecording() {
        if (state is UiState.Recording) { stopAndTranslate(); return }
        if (state is UiState.NoModel || state is UiState.Decoding || state is UiState.Listening || state is UiState.WritingUrdu || state is UiState.WritingEnglish) return
        speech?.stopSpeaking()
        audio = AudioState.None
        copied = ""
        val rec = Recorder()
        val begun = System.currentTimeMillis()
        try {
            rec.start(
                onLevel = { level -> post { (state as? UiState.Recording)?.let { state = UiState.Recording(level, ((System.currentTimeMillis() - begun) / 1000).toInt()) } } },
                onLimit = { post { stopAndTranslate() } },
            )
        } catch (t: Throwable) {
            LlmEngine.saveError(ctx, t)
            state = UiState.Failed
            return
        }
        recorder = rec
        state = UiState.Recording(0f, 0)
    }

    private fun stopAndTranslate() {
        val rec = recorder ?: return
        recorder = null
        val samples = rec.stop()
        if (samples.size < Pcm.RATE / 2 || Pcm.isSilent(samples)) { state = UiState.TooQuiet; return }
        state = UiState.Listening(1, 1)
        io.execute {
            try {
                val pieces = Pcm.split(samples)
                val parts = ArrayList<String>()
                pieces.forEachIndexed { i, piece ->
                    post { state = UiState.Listening(i + 1, pieces.size) }
                    parts += VoicePrompts.cleanTranscript(LlmEngine.listen(ctx, piece, VoicePrompts.ENGLISH_TRANSCRIPT))
                }
                val english = VoicePrompts.join(parts)
                if (english.isEmpty()) { post { state = UiState.TooQuiet }; return@execute }

                post { state = UiState.WritingUrdu }
                var urdu = LlmEngine.askText(ctx, VoicePrompts.URDU_SCRIPT_RULES, english)
                if (!VoicePrompts.isUrduScript(urdu)) {
                    urdu = LlmEngine.askText(ctx, VoicePrompts.URDU_SCRIPT_RULES + VoicePrompts.URDU_SCRIPT_RETRY, english)
                }
                if (!VoicePrompts.isUrduScript(urdu)) urdu = ""
                val (style, auto) = RomanUrdu.toneStyleFor(ctx, english)
                val roman = try { LlmEngine.translate(ctx, english, style, auto) } catch (t: Throwable) { "" }

                post {
                    state = UiState.Done(urdu, english, roman, false)
                    audio = if (urdu.isEmpty()) AudioState.Failed else AudioState.Making
                }
                if (urdu.isNotEmpty()) makeAudio(urdu)
            } catch (t: Throwable) {
                LlmEngine.saveError(ctx, t)
                post { state = UiState.Failed }
            }
        }
    }

    private fun speechEngine(): UrduSpeech = speech ?: UrduSpeech(ctx).also { speech = it }

    /** Speaks the Urdu into a WAV, then an .m4a. Runs on the worker thread. */
    private fun makeAudio(urdu: String) {
        try {
            val s = speechEngine()
            if (s.check() != UrduSpeech.Availability.READY) {
                post { audio = AudioState.VoiceMissing }
                return
            }
            val dir = File(ctx.cacheDir, "voice").apply { mkdirs() }
            val wav = File(dir, "urdu.wav")
            if (!s.toWav(urdu, wav)) { post { audio = AudioState.Failed }; return }
            val m4a = File(dir, "urdu-voice.m4a")
            M4a.fromWav(wav, m4a)
            wav.delete()
            post { audio = AudioState.Ready(m4a) }
        } catch (t: Throwable) {
            LlmEngine.saveError(ctx, t)
            post { audio = AudioState.Failed }
        }
    }

    /** Plays the Urdu aloud on the phone. */
    fun play(urdu: String) {
        io.execute { speechEngine().speak(urdu) }
    }

    /** Try again after installing the Urdu voice. */
    fun retryAudio() {
        val s = state as? UiState.Done ?: return
        if (s.urdu.isEmpty()) return
        audio = AudioState.Making
        io.execute { makeAudio(s.urdu) }
    }

    fun copy(label: String, text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = label
    }

    fun resetForAnother() {
        speech?.stopSpeaking()
        audio = AudioState.None
        copied = ""
        state = if (LlmEngine.modelExists(ctx)) UiState.Idle else UiState.NoModel
    }

    fun message(id: Int, vararg a: Any) = str(id, *a)

    override fun onCleared() {
        recorder?.stop()
        speech?.shutdown()
        main.removeCallbacksAndMessages(null)
    }
}
