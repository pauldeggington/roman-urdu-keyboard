package com.paul.urdukb.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Slows speech down without changing its pitch (WSOLA: pieces of the sound are overlapped and each piece is placed where it
 * lines up best with the one before). Slower speech can be easier for the model to catch word by word. Plain Kotlin, tested.
 */
object Stretch {

    private const val FRAME = 512          // 32 ms at 16 kHz
    private const val HOP = FRAME / 2
    private const val SEARCH = 128         // how far a piece may move to line up (8 ms)

    /** [factor] above 1 makes the audio longer (slower): 1.25 is 25% slower. */
    fun slower(samples: ShortArray, factor: Double): ShortArray = change(samples, factor)

    /** [factor] below 1 makes the audio shorter (faster): 0.85 is 15% faster. */
    fun faster(samples: ShortArray, factor: Double): ShortArray = change(samples, factor)

    /** Changes the speed by [factor] (length after = length before times factor), keeping the pitch. */
    fun change(samples: ShortArray, factor: Double): ShortArray {
        if (abs(factor - 1.0) < 0.0001 || factor < 0.4 || factor > 3.0 || samples.size < FRAME * 2) return samples
        val n = samples.size
        val outLen = (n * factor).roundToInt() + FRAME
        val out = FloatArray(outLen)
        val norm = FloatArray(outLen)
        val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / FRAME)).toFloat() }
        val x = FloatArray(n) { samples[it].toFloat() }
        val analysisHop = HOP / factor

        fun add(from: Int, at: Int) {
            for (i in 0 until FRAME) {
                if (at + i >= outLen) break
                out[at + i] += x[from + i] * window[i]
                norm[at + i] += window[i]
            }
        }

        var chosen = 0
        add(0, 0)
        var k = 1
        while (true) {
            val outPos = k * HOP
            if (outPos + FRAME > outLen) break
            val nominal = (k * analysisHop).roundToInt()
            if (nominal + FRAME > n + SEARCH) break
            // what would have come next in the original, if the last piece kept playing
            val natural = (chosen + HOP).coerceAtMost(n - FRAME)
            var best = nominal.coerceIn(0, n - FRAME)
            var bestScore = Double.NEGATIVE_INFINITY
            val lo = (nominal - SEARCH).coerceAtLeast(0)
            val hi = (nominal + SEARCH).coerceAtMost(n - FRAME)
            var s = lo
            while (s <= hi) {
                var dot = 0.0
                var i = 0
                while (i < FRAME) { dot += x[s + i] * x[natural + i]; i += 2 }
                if (dot > bestScore) { bestScore = dot; best = s }
                s += 1
            }
            add(best, outPos)
            chosen = best
            k++
        }
        // the end of the original may be left over: add its last frame so the tail is not cut
        val used = ShortArray(outLen) { i -> if (norm[i] > 1e-3f) (out[i] / norm[i]).roundToInt().coerceIn(-32768, 32767).toShort() else 0 }
        val end = ((k - 1) * HOP + FRAME).coerceAtMost(outLen)
        // trim trailing silence caused by the unused tail of the buffer
        return used.copyOf(maxOf(end, 1))
    }

    /** Average absolute level, for tests. */
    internal fun level(s: ShortArray): Double = if (s.isEmpty()) 0.0 else s.sumOf { abs(it.toDouble()) } / s.size
}
