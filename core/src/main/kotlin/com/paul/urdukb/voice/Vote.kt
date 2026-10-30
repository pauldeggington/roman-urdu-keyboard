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

    /** The other readings that differ from [chosen], one of each, at most [max]: what else the passes heard. */
    fun alternatives(chosen: String, candidates: List<String>, max: Int = 3): List<String> {
        val seen = hashSetOf(key(chosen))
        val out = ArrayList<String>()
        for (c in candidates) {
            val k = key(c)
            if (k.isEmpty() || !seen.add(k)) continue
            out += c.trim()
            if (out.size >= max) break
        }
        return out
    }
}
