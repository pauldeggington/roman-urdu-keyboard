package com.paul.urdukb

import kotlin.math.ln

/**
 * The example bank: any number of "input | output" lines. For each message the keyboard shows the model
 * only the few closest ones (shared rare words count most), so a bigger bank makes answers more fitting
 * without making the prompt longer.
 */
object Bank {

    private val TOKEN = Regex("[\\p{L}']+")

    fun parse(text: String): List<Pair<String, String>> =
        text.lines().mapNotNull { line ->
            val i = line.indexOf('|')
            if (i <= 0) return@mapNotNull null
            val a = line.substring(0, i).trim()
            val b = line.substring(i + 1).trim()
            if (a.isEmpty() || b.isEmpty()) null else a to b
        }

    private fun tokens(s: String): Set<String> = TOKEN.findAll(s.lowercase()).map { it.value }.toSet()

    private fun trigrams(s: String): Set<String> {
        val t = " " + s.lowercase().filter { it.isLetter() || it == ' ' } + " "
        if (t.length < 3) return emptySet()
        return (0..t.length - 3).map { t.substring(it, it + 3) }.toSet()
    }

    /** The [k] closest examples, closest last (models pay most attention to what comes last). */
    fun pick(bank: List<Pair<String, String>>, input: String, k: Int = 6): List<Pair<String, String>> {
        if (bank.size <= k) return bank

        val docTokens = bank.map { tokens(it.first) }
        val df = HashMap<String, Int>()
        for (set in docTokens) for (t in set) df[t] = (df[t] ?: 0) + 1
        val n = bank.size.toDouble()
        fun idf(t: String) = ln((n + 1.0) / ((df[t] ?: 0) + 1.0)) + 1.0

        val q = tokens(input)
        val qGrams = trigrams(input)
        var qWeight = 0.0
        for (t in q) qWeight += idf(t)

        val scored = bank.mapIndexed { i, ex ->
            var overlap = 0.0
            for (t in q) if (t in docTokens[i]) overlap += idf(t)
            val tokenScore = if (qWeight > 0) overlap / qWeight else 0.0
            val g = trigrams(ex.first)
            val dice = if (qGrams.isEmpty() || g.isEmpty()) 0.0 else 2.0 * qGrams.intersect(g).size / (qGrams.size + g.size)
            Triple(ex, 0.7 * tokenScore + 0.3 * dice, i)
        }
        return scored.sortedWith(compareByDescending<Triple<Pair<String, String>, Double, Int>> { it.second }.thenBy { it.third })
            .take(k)
            .reversed()
            .map { it.first }
    }
}
