package com.paul.urdukb.ime

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** A finger position, in pixels. (Plain class so the decoder can be tested without Android.) */
class GlidePoint(val x: Float, val y: Float)

/** Where each letter key is, and how wide a key is. */
class GlideLayout(val centers: Map<Char, GlidePoint>, val keyWidth: Float)

/**
 * Turns a finger path across the letter keys into words (glide typing).
 *
 * For each dictionary word the "ideal path" is the line through its letters' keys. A word is a candidate when the
 * path starts near its first letter, ends near its last letter, and passes close to every letter in between. Candidates
 * are then ranked by how closely the resampled paths match, with a bonus for common words.
 */
class GlideDecoder(entries: List<Entry>) {

    class Entry(val word: String, val key: String, val zipf: Int)

    /** Words grouped by their first letter, so a glide only looks at words that start near where the finger started. */
    private val byFirst: Map<Char, List<Entry>> = entries.groupBy { it.key[0] }

    companion object {
        private const val SAMPLES = 32
        private const val START_RADIUS = 1.6f   // in key widths
        private const val COVER_RADIUS = 1.15f
        private const val WEIGHT_SHAPE = 1.0f
        private const val WEIGHT_FREQUENCY = 0.3f
        private const val WEIGHT_ENDS = 0.4f      // how close the finger started and stopped to the first and last letter

        /** Lines of "word<TAB>frequency", most common first. Words with fewer than two different letters are skipped. */
        fun parse(lines: Sequence<String>): GlideDecoder = fromWords(WordSuggester.readWords(lines))

        fun fromWords(words: List<Pair<String, Int>>): GlideDecoder {
            val out = ArrayList<Entry>(words.size)
            for ((word, zipf) in words) {
                val sb = StringBuilder()
                for (ch in word) {
                    if (ch !in 'a'..'z') continue            // apostrophes are not on the glide path
                    if (sb.isEmpty() || sb[sb.length - 1] != ch) sb.append(ch)   // "hello" glides as h-e-l-o
                }
                if (sb.length >= 2) out += Entry(word, sb.toString(), zipf)
            }
            return GlideDecoder(out)
        }
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float) = hypot(ax - bx, ay - by)

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 == 0f) return dist(px, py, ax, ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return dist(px, py, ax + t * dx, ay + t * dy)
    }

    /** [n] points evenly spaced along the polyline given as x and y arrays. */
    private fun resample(xs: FloatArray, ys: FloatArray, n: Int): Pair<FloatArray, FloatArray> {
        val len = FloatArray(xs.size)
        for (i in 1 until xs.size) len[i] = len[i - 1] + dist(xs[i - 1], ys[i - 1], xs[i], ys[i])
        val total = len[xs.size - 1]
        val ox = FloatArray(n)
        val oy = FloatArray(n)
        if (total == 0f) {
            ox.fill(xs[0]); oy.fill(ys[0])
            return ox to oy
        }
        var j = 0
        for (i in 0 until n) {
            val t = total * i / (n - 1)
            while (j < xs.size - 2 && len[j + 1] < t) j++
            val seg = len[j + 1] - len[j]
            val f = if (seg == 0f) 0f else (t - len[j]) / seg
            ox[i] = xs[j] + (xs[j + 1] - xs[j]) * f
            oy[i] = ys[j] + (ys[j + 1] - ys[j]) * f
        }
        return ox to oy
    }

    /** The best words for the path, most likely first. Empty when the path is too short or nothing fits. */
    fun decode(path: List<GlidePoint>, layout: GlideLayout, max: Int = 5): List<String> {
        val kw = layout.keyWidth
        // drop points that did not move
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        for (p in path) {
            if (px.isEmpty() || dist(px.last(), py.last(), p.x, p.y) >= 1f) { px.add(p.x); py.add(p.y) }
        }
        if (px.size < 2) return emptyList()
        val ix = px.toFloatArray()
        val iy = py.toFloatArray()
        var pathLen = 0f
        for (i in 1 until ix.size) pathLen += dist(ix[i - 1], iy[i - 1], ix[i], iy[i])
        if (pathLen < 0.8f * kw) return emptyList()
        val (rx, ry) = resample(ix, iy, SAMPLES)
        val sx = ix[0]; val sy = iy[0]
        val ex = ix[ix.size - 1]; val ey = iy[iy.size - 1]

        // letters whose key is near where the finger started / stopped
        val startKeys = layout.centers.filter { dist(sx, sy, it.value.x, it.value.y) <= START_RADIUS * kw }.keys
        val endKeys = layout.centers.filter { dist(ex, ey, it.value.x, it.value.y) <= START_RADIUS * kw }.keys
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val cover = COVER_RADIUS * kw
        val scored = ArrayList<Pair<Float, String>>()
        for (e in startKeys.flatMap { byFirst[it].orEmpty() }) {
            val k = e.key
            if (k[k.length - 1] !in endKeys) continue
            var ok = true
            var i = 1
            while (i < k.length - 1 && ok) {
                val c = layout.centers[k[i]] ?: run { ok = false; null }
                if (c != null) {
                    var best = Float.MAX_VALUE
                    for (s in 0 until ix.size - 1) {
                        val d = segDist(c.x, c.y, ix[s], iy[s], ix[s + 1], iy[s + 1])
                        if (d < best) best = d
                    }
                    if (best > cover) ok = false
                }
                i++
            }
            if (!ok) continue
            val tx = FloatArray(k.length)
            val ty = FloatArray(k.length)
            var missing = false
            for (j in k.indices) {
                val c = layout.centers[k[j]]
                if (c == null) { missing = true; break }
                tx[j] = c.x; ty[j] = c.y
            }
            if (missing) continue
            val (qx, qy) = resample(tx, ty, SAMPLES)
            var sum = 0f
            for (j in 0 until SAMPLES) sum += dist(rx[j], ry[j], qx[j], qy[j])
            val shape = sum / SAMPLES / kw
            val first = layout.centers.getValue(k[0])
            val last = layout.centers.getValue(k[k.length - 1])
            val ends = (dist(sx, sy, first.x, first.y) + dist(ex, ey, last.x, last.y)) / kw
            scored += (WEIGHT_SHAPE * shape + WEIGHT_ENDS * ends - WEIGHT_FREQUENCY * e.zipf / 100f) to e.word
        }
        scored.sortBy { it.first }
        val out = ArrayList<String>()
        for ((_, w) in scored) {
            if (w !in out) out += w
            if (out.size >= max) break
        }
        return out
    }
}
