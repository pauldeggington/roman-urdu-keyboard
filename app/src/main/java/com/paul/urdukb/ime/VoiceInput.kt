package com.paul.urdukb.ime

import com.paul.urdukb.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputConnection

/**
 * Voice typing inside the keyboard: the phone's speech recogniser runs while the keyboard stays on screen,
 * and what it hears appears in the text field as you speak. Keeps listening through pauses until stopped.
 */
class VoiceInput(
    private val ctx: Context,
    private val connection: () -> InputConnection?,
    private val onState: (Boolean) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    var listening = false
        private set
    private var quietRounds = 0

    companion object {
        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)
        fun hasPermission(ctx: Context) =
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (listening) return
        if (!available(ctx)) { onProblem(ctx.getString(R.string.voice_none)); return }
        if (!hasPermission(ctx)) { onProblem("NEEDS_PERMISSION"); return }
        listening = true
        quietRounds = 0
        onState(true)
        begin()
    }

    fun stop() {
        if (!listening) return
        listening = false
        main.removeCallbacksAndMessages(null)
        connection()?.finishComposingText()
        release()
        onState(false)
    }

    private fun begin() {
        if (!listening) return
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    quietRounds = 0
                    connection()?.commitText(text.trim() + " ", 1)
                } else connection()?.finishComposingText()
                again()
            }

            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { quietRounds = 0; connection()?.setComposingText(text, 1) }
            }

            override fun onError(error: Int) {
                connection()?.finishComposingText()
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (++quietRounds >= 3) { stop() } else again()     // nobody is talking: stop after a few quiet rounds
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> main.postDelayed({ begin() }, 400)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stop(); onProblem("NEEDS_PERMISSION") }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> { stop(); onProblem(ctx.getString(R.string.voice_network)) }
                    else -> { stop(); onProblem(ctx.getString(R.string.voice_stopped, error)) }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        r.startListening(i)
    }

    private fun again() { if (listening) main.postDelayed({ begin() }, 150) }

    private fun release() {
        rec?.let { try { it.cancel(); it.destroy() } catch (_: Exception) {} }
        rec = null
    }
}
