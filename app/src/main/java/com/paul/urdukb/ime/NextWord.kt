package com.paul.urdukb.ime

import android.content.Context

/**
 * Guesses the next word after a finished one: first from pairs of words the person types on this phone,
 * then from a small built-in table of everyday English. English only; the table holds no Roman Urdu.
 */
object NextWord {
    private var table: Map<String, List<String>>? = null

    @Synchronized
    private fun builtIn(ctx: Context): Map<String, List<String>> {
        table?.let { return it }
        val m = HashMap<String, List<String>>(256)
        try {
            ctx.assets.open("dict/bigrams.tsv").bufferedReader().useLines { lines ->
                for (ln in lines) {
                    val t = ln.indexOf('\t')
                    if (t > 0) m[ln.substring(0, t)] = ln.substring(t + 1).split(' ').filter { it.isNotEmpty() }
                }
            }
        } catch (_: Exception) {
        }
        table = m
        return m
    }

    /** [prev] is the word just finished (lowercase), or "<s>" at the start of a sentence. */
    fun predict(ctx: Context, prev: String, max: Int = 3): List<String> {
        val out = LinkedHashSet<String>()
        for ((w, n) in PersonalWords.followers(ctx, prev)) if (n >= 2) out += w
        for (w in builtIn(ctx)[prev].orEmpty()) out += w
        return out.take(max)
    }
}
