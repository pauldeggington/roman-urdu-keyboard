package com.paul.urdukb.ime

import android.content.Context

/**
 * Words the person types often (names, Roman Urdu spellings, slang), kept only on this phone so the keyboard can
 * suggest them. Not collected in password, email or address fields, and never when the app asks for no learning.
 */
object PersonalWords {
    private const val KEY = "personal_words"
    private const val MAX = 1500

    private const val KEY_PAIRS = "personal_pairs"
    private const val MAX_PAIRS = 3000

    private val counts = HashMap<String, Int>()
    private val pairs = HashMap<String, Int>()      // "previous next" -> times typed
    private var loaded = false
    private var dirty = 0

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = Kb.prefs(ctx).getString(KEY, "").orEmpty()
        for (part in raw.split(',')) {
            val i = part.lastIndexOf(':')
            if (i <= 0) continue
            val n = part.substring(i + 1).toIntOrNull() ?: continue
            counts[part.substring(0, i)] = n
        }
        for (part in Kb.prefs(ctx).getString(KEY_PAIRS, "").orEmpty().split(',')) {
            val i = part.lastIndexOf(':')
            if (i <= 0) continue
            pairs[part.substring(0, i)] = part.substring(i + 1).toIntOrNull() ?: continue
        }
    }

    /** Two finished words in a row, so the keyboard can offer the second after the first. */
    @Synchronized
    fun learnPair(ctx: Context, prev: String, next: String) {
        ensure(ctx)
        val a = prev.lowercase(); val b = next.lowercase()
        if (a.length < 1 || b.length < 2 || a.length > 24 || b.length > 24) return
        if ((a + b).any { !(it.isLetter() || it == '\'') }) return
        val k = "$a $b"
        pairs[k] = (pairs[k] ?: 0) + 1
        if (pairs.size > MAX_PAIRS) pairs.entries.sortedBy { it.value }.take(pairs.size - MAX_PAIRS + 200).forEach { pairs.remove(it.key) }
        dirty++
        if (dirty >= 20) save(ctx)
    }

    /** Words typed after [prev], most often first. */
    @Synchronized
    fun followers(ctx: Context, prev: String): List<Pair<String, Int>> {
        ensure(ctx)
        val p = "$prev "
        return pairs.entries.filter { it.key.startsWith(p) }.map { it.key.substring(p.length) to it.value }.sortedByDescending { it.second }
    }

    /** Words typed at least twice: one slip of the finger does not make a word the person "uses". */
    @Synchronized
    fun snapshot(ctx: Context): Map<String, Int> {
        ensure(ctx)
        val out = HashMap<String, Int>()
        for ((w, n) in counts) if (n >= 2) out[w] = n
        return out
    }

    /** A finished word the person typed. Only worth keeping if the built-in word list does not already know it well. */
    @Synchronized
    fun learn(ctx: Context, word: String) {
        ensure(ctx)
        val w = word.lowercase()
        if (w.length < 3 || w.length > 24 || w.any { !(it.isLetter() || it == '\'') }) return
        counts[w] = (counts[w] ?: 0) + 1
        if (counts.size > MAX) {
            // drop the least used
            counts.entries.sortedBy { it.value }.take(counts.size - MAX + 100).forEach { counts.remove(it.key) }
        }
        dirty++
        if (dirty >= 20) save(ctx)
    }

    @Synchronized
    fun save(ctx: Context) {
        if (dirty == 0) return
        dirty = 0
        Kb.prefs(ctx).edit()
            .putString(KEY, counts.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString(KEY_PAIRS, pairs.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    @Synchronized
    fun clear(ctx: Context) {
        ensure(ctx)
        counts.clear()
        pairs.clear()
        dirty = 0
        Kb.prefs(ctx).edit().remove(KEY).remove(KEY_PAIRS).apply()
    }

    @Synchronized
    fun size(ctx: Context): Int {
        ensure(ctx)
        return counts.size + pairs.size
    }
}
