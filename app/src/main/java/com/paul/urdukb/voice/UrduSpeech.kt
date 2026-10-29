package com.paul.urdukb.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Speaks Urdu with the phone's own text-to-speech voice, and can save it to a file. It only works if an Urdu voice is
 * installed (Google's speech engine has "Urdu (Pakistan)" as a download); [check] says what is missing.
 */
class UrduSpeech(ctx: Context) {

    enum class Availability { READY, VOICE_MISSING, NO_ENGINE }

    private val app = ctx.applicationContext
    private val ready = CountDownLatch(1)
    @Volatile private var initOk = false
    private val tts: TextToSpeech = TextToSpeech(app) { status ->
        initOk = status == TextToSpeech.SUCCESS
        ready.countDown()
    }

    private val urdu = Locale("ur", "PK")

    /** Blocking (waits for the engine to start, at most a few seconds). */
    fun check(): Availability {
        if (!ready.await(6, TimeUnit.SECONDS) || !initOk) return Availability.NO_ENGINE
        val r = tts.isLanguageAvailable(urdu)
        return if (r >= TextToSpeech.LANG_AVAILABLE) Availability.READY else Availability.VOICE_MISSING
    }

    /** Speaks [text] now (queue is flushed). */
    fun speak(text: String) {
        if (check() != Availability.READY) return
        tts.language = urdu
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() { runCatching { tts.stop() } }

    /** Writes the spoken [text] to [file] as a WAV. Blocking. Returns false when it could not be done. */
    fun toWav(text: String, file: File): Boolean {
        if (check() != Availability.READY) return false
        tts.language = urdu
        val done = CountDownLatch(1)
        val ok = java.util.concurrent.atomic.AtomicBoolean(false)
        val id = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) { ok.set(true); done.countDown() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) done.countDown() }
        })
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val queued = tts.synthesizeToFile(text, null, file, id)
        if (queued != TextToSpeech.SUCCESS) return false
        done.await(90, TimeUnit.SECONDS)
        return ok.get() && file.length() > 44
    }

    fun shutdown() { runCatching { tts.stop(); tts.shutdown() } }
}
