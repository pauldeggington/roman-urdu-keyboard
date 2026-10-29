package com.paul.urdukb.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Records the microphone as 16 kHz mono samples, for English speech. The caller checks the RECORD_AUDIO permission first. */
class Recorder {

    /** At most this long; recording stops by itself after it. */
    companion object { const val MAX_SECONDS = 2 * 60 }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var samples = ShortArray(Pcm.RATE * 30)
    private var size = 0

    /** [onLevel] gets the loudness (0 to 1) about ten times a second; [onLimit] is called if the time limit is reached. */
    @SuppressLint("MissingPermission")
    fun start(onLevel: (Float) -> Unit, onLimit: () -> Unit) {
        if (running) return
        val min = AudioRecord.getMinBufferSize(Pcm.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, Pcm.RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, Pcm.RATE),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); throw IllegalStateException("The microphone could not be opened.") }
        size = 0
        running = true
        rec.startRecording()
        thread = Thread({
            val buf = ShortArray(Pcm.RATE / 10)
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (size + n > samples.size) samples = samples.copyOf(samples.size * 2)
                    System.arraycopy(buf, 0, samples, size, n)
                    size += n
                    onLevel(Pcm.peak(buf, 0, n))
                    if (size >= MAX_SECONDS * Pcm.RATE) { running = false; onLimit() }
                }
            } finally {
                try { rec.stop() } catch (_: Throwable) {}
                rec.release()
            }
        }, "voice-recorder").apply { start() }
    }

    /** Stops and returns what was recorded. */
    fun stop(): ShortArray {
        running = false
        thread?.join(1500)
        thread = null
        return samples.copyOf(size)
    }

    val isRunning: Boolean get() = running
}
