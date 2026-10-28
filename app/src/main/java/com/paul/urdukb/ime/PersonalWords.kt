package com.paul.urdukb.ime

import android.content.Context

/**
 * Words the person types often (names, Roman Urdu spellings, slang), kept only on this phone so the keyboard can
 * suggest them. Not collected in password, email or address fields, and never when the app asks for no learning.
 */
object PersonalWords {
    private const val KEY = "personal_words"
    private const val MAX = 1500

    private val counts = HashMap<String, Int>()
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
    }

    @Synchronized
    fun snapshot(ctx: Context): Map<String, Int> {
        ensure(ctx)
        return HashMap(counts)
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
        Kb.prefs(ctx).edit().putString(KEY, counts.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    @Synchronized
    fun clear(ctx: Context) {
        ensure(ctx)
        counts.clear()
        dirty = 0
        Kb.prefs(ctx).edit().remove(KEY).apply()
    }

    @Synchronized
    fun size(ctx: Context): Int {
        ensure(ctx)
        return counts.size
    }
}
