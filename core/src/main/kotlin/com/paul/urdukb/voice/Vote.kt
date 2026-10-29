package com.paul.urdukb.voice

/** Picks the transcript that most of the listening passes agree on. */
object Vote {

    private fun key(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** The text that at least two of [candidates] say (ignoring spaces and punctuation), or null when no two agree. Empty ones do not vote. */
    fun majority(candidates: List<String>): String? {
        val groups = candidates.filter { key(it).isNotEmpty() }.groupBy { key(it) }
        val top = groups.values.maxByOrNull { it.size } ?: return null
        return if (top.size >= 2) top.first() else null
    }
}
