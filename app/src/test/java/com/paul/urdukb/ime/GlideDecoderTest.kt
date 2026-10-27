package com.paul.urdukb.ime

import java.io.File
import java.util.Random
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Glides are simulated: a smooth curve through the word's letters, each letter missed by a random amount and the
 * path wobbling a little, like a real finger. The decoder has to get the word back.
 */
class GlideDecoderTest {

    private val keyW = 108f      // 1080 px wide screen, ten keys
    private val rowH = 170f
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val rowOffset = listOf(0f, 0.5f, 1.5f)

    private val layout = GlideLayout(
        buildMap {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, ch -> put(ch, GlidePoint((i + 0.5f + rowOffset[r]) * keyW, r * rowH + rowH / 2)) }
            }
        },
        keyW,
    )

    private val lines = File("src/main/assets/dict/en.tsv").readLines()
    private val decoder = GlideDecoder.parse(lines.asSequence())

    private class Word(val word: String, val key: String, val zipf: Int)

    private val words: List<Word> = lines.take(20000).mapNotNull { line ->
        val (w, z) = line.split('\t')
        val sb = StringBuilder()
        for (c in w) if (c in 'a'..'z' && (sb.isEmpty() || sb.last() != c)) sb.append(c)
        if (sb.length < 2) null else Word(w, sb.toString(), z.toInt())
    }

    private fun simulate(key: String, rnd: Random, jitter: Float, fine: Float): List<GlidePoint> {
        var pts = key.map { c ->
            val p = layout.centers.getValue(c)
            GlidePoint(p.x + rnd.nextGaussian().toFloat() * jitter * keyW, p.y + rnd.nextGaussian().toFloat() * jitter * keyW)
        }
        if (pts.size == 2) pts = listOf(pts[0], GlidePoint((pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2), pts[1])
        val p = listOf(pts.first()) + pts + listOf(pts.last())
        val out = ArrayList<GlidePoint>()
        for (i in 1 until p.size - 2) {
            val (p0, p1, p2, p3) = listOf(p[i - 1], p[i], p[i + 1], p[i + 2])
            val steps = max(4, (hypot(p2.x - p1.x, p2.y - p1.y) / 12f).toInt())
            for (s in 0 until steps) {
                val t = s / steps.toFloat()
                fun cr(a: Float, b: Float, c: Float, d: Float) =
                    0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t)
                out += GlidePoint(
                    cr(p0.x, p1.x, p2.x, p3.x) + rnd.nextGaussian().toFloat() * fine * keyW,
                    cr(p0.y, p1.y, p2.y, p3.y) + rnd.nextGaussian().toFloat() * fine * keyW,
                )
            }
        }
        out += pts.last()
        return out
    }

    private fun accuracy(jitter: Float, n: Int, seed: Long): Pair<Double, Double> {
        val rnd = Random(seed)
        // common words come up more often, like in real typing
        val weights = words.map { 10.0.pow(it.zipf / 100.0) }
        val total = weights.sum()
        var top1 = 0
        var top3 = 0
        repeat(n) {
            var r = rnd.nextDouble() * total
            var idx = 0
            while (idx < words.size - 1 && r > weights[idx]) { r -= weights[idx]; idx++ }
            val w = words[idx]
            val result = decoder.decode(simulate(w.key, rnd, jitter, 0.04f), layout, 5)
            if (result.firstOrNull() == w.word) top1++
            if (w.word in result.take(3)) top3++
        }
        return top1 / n.toDouble() to top3 / n.toDouble()
    }

    @Test
    fun carefulGlidesFindTheWord() {
        val (t1, t3) = accuracy(0.28f, 400, 7)
        println("careful glides: first=$t1 top3=$t3")
        assertTrue(t1 >= 0.75, "first-choice accuracy was $t1")
        assertTrue(t3 >= 0.92, "top-3 accuracy was $t3")
    }

    @Test
    fun sloppyGlidesStillMostlyWork() {
        val (t1, t3) = accuracy(0.45f, 400, 11)
        println("sloppy glides: first=$t1 top3=$t3")
        assertTrue(t1 >= 0.65, "first-choice accuracy was $t1")
        assertTrue(t3 >= 0.85, "top-3 accuracy was $t3")
    }

    @Test
    fun commonWordsAreInTheTopThree() {
        val rnd = Random(3)
        for (w in listOf("hello", "world", "thanks", "tomorrow", "because", "message", "working", "evening", "please", "morning")) {
            val key = StringBuilder().also { sb -> for (c in w) if (sb.isEmpty() || sb.last() != c) sb.append(c) }.toString()
            val result = decoder.decode(simulate(key, rnd, 0.3f, 0.04f), layout, 3)
            assertTrue(w in result, "$w was not in $result")
        }
    }

    @Test
    fun aTapIsNotAGlide() {
        val p = layout.centers.getValue('h')
        assertTrue(decoder.decode(listOf(p, GlidePoint(p.x + 3, p.y)), layout).isEmpty())
    }
}
