package com.paul.urdukb.voice

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Plain audio helpers for the voice features: whatever the phone decodes (48 kHz stereo, say) becomes 16 kHz mono, which is
 * what Gemma hears, long recordings are cut into pieces that fit its 30-second limit, and a piece can be written as a
 * WAV file. All plain Kotlin, so it is tested on a computer.
 */
object Pcm {

    /** The sample rate the model listens at. */
    const val RATE = 16_000

    /** The longest piece the model takes is 30 seconds; pieces are cut a little shorter, at a quiet moment. */
    const val MAX_PIECE_SECONDS = 28

    /** Anything quieter than this (average level, 16-bit scale) over a whole recording is silence. */
    private const val SILENCE_RMS = 25.0

    /** [samples] holds [channels] interleaved channels at [rate] Hz. Returns 16 kHz mono. */
    fun toMono16k(samples: ShortArray, rate: Int, channels: Int): ShortArray {
        require(rate > 0 && channels > 0) { "bad audio format" }
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += samples[f * channels + c]
            mono[f] = sum.toFloat() / channels
        }
        if (rate == RATE) return ShortArray(frames) { mono[it].toInt().coerceIn(-32768, 32767).toShort() }
        val outLen = (frames.toLong() * RATE / rate).toInt()
        val out = ShortArray(outLen)
        val step = rate.toDouble() / RATE
        for (i in 0 until outLen) {
            val v = if (step > 1.0) {
                // fewer samples out than in: average the input span, which also filters what 16 kHz cannot hold
                val a = i * step
                val b = minOf((i + 1) * step, frames.toDouble())
                var lo = a.toInt()
                var acc = 0.0
                var wsum = 0.0
                while (lo < b && lo < frames) {
                    val w = minOf(b, lo + 1.0) - maxOf(a, lo.toDouble())
                    if (w > 0) { acc += mono[lo] * w; wsum += w }
                    lo++
                }
                if (wsum > 0) acc / wsum else 0.0
            } else {
                val pos = i * step
                val i0 = pos.toInt().coerceAtMost(frames - 1)
                val i1 = (i0 + 1).coerceAtMost(frames - 1)
                val frac = pos - i0
                mono[i0] * (1 - frac) + mono[i1] * frac
            }
            out[i] = v.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    fun seconds(samples: ShortArray): Double = samples.size.toDouble() / RATE

    private fun rms(s: ShortArray, from: Int, to: Int): Double {
        if (to <= from) return 0.0
        var acc = 0.0
        for (i in from until to) acc += s[i].toDouble() * s[i]
        return sqrt(acc / (to - from))
    }

    /** True when the whole recording is (nearly) silent, so there is nothing to translate. */
    fun isSilent(samples: ShortArray): Boolean = samples.isEmpty() || rms(samples, 0, samples.size) < SILENCE_RMS

    /**
     * Cuts a long recording into pieces of at most [maxSeconds]. Each cut is made at the quietest moment in the last
     * [searchSeconds] of a piece, so a word is not split in half. Very short leftovers are joined to the piece before.
     */
    fun split(samples: ShortArray, maxSeconds: Int = MAX_PIECE_SECONDS, searchSeconds: Double = 4.0): List<ShortArray> {
        val max = maxSeconds * RATE
        if (samples.size <= max) return listOf(samples)
        val frame = RATE / 10                     // 100 ms
        val search = (searchSeconds * RATE).toInt().coerceAtMost(max / 2)
        val pieces = ArrayList<ShortArray>()
        var pos = 0
        while (samples.size - pos > max) {
            val target = pos + max
            var bestAt = target
            var bestLevel = Double.MAX_VALUE
            var at = target - search
            while (at + frame <= target) {
                val level = rms(samples, at, at + frame)
                if (level < bestLevel) { bestLevel = level; bestAt = at + frame / 2 }
                at += frame / 2
            }
            pieces += samples.copyOfRange(pos, bestAt)
            pos = bestAt
        }
        val rest = samples.copyOfRange(pos, samples.size)
        // a leftover under half a second is joined to the last piece, which has room (pieces are cut under 30 s)
        if (rest.size < RATE / 2 && pieces.isNotEmpty() && pieces.last().size + rest.size <= 29 * RATE) {
            pieces[pieces.lastIndex] = pieces.last() + rest
        } else pieces += rest
        return pieces
    }

    /** A 16 kHz mono 16-bit WAV file (header plus samples). */
    fun wav(samples: ShortArray): ByteArray {
        val dataLen = samples.size * 2
        val out = ByteArray(44 + dataLen)
        fun putInt(at: Int, v: Int) { for (k in 0..3) out[at + k] = (v shr (8 * k)).toByte() }
        fun putShort(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte() }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        putInt(4, 36 + dataLen)
        "WAVE".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> out[12 + i] = c.code.toByte() }
        putInt(16, 16)           // PCM header size
        putShort(20, 1)          // PCM
        putShort(22, 1)          // mono
        putInt(24, RATE)
        putInt(28, RATE * 2)     // bytes per second
        putShort(32, 2)          // bytes per frame
        putShort(34, 16)         // bits
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        putInt(40, dataLen)
        for (i in samples.indices) putShort(44 + i * 2, samples[i].toInt())
        return out
    }

    /** The samples alone, 16-bit little-endian, no header. */
    fun raw(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) { out[i * 2] = samples[i].toInt().toByte(); out[i * 2 + 1] = (samples[i].toInt() shr 8).toByte() }
        return out
    }

    /** Peak level, 0 to 1: for a level meter while recording. */
    fun peak(samples: ShortArray, from: Int = 0, to: Int = samples.size): Float {
        var p = 0
        for (i in from until to) p = maxOf(p, abs(samples[i].toInt()))
        return p / 32768f
    }
}
