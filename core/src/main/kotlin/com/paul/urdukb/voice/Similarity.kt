package com.paul.urdukb.voice

/**
 * How alike two Urdu texts are, from 0 to 1, by the three-letter groups they share (Dice). Only Urdu-script letters count,
 * so English words and names mixed into a transcript do not matter. Used to check an English translation by turning it
 * back into Urdu and comparing with what was heard: a translation that lost the meaning does not come back alike.
 */
object Similarity {

    /** Below this many three-letter groups on either side there is too little Urdu to judge. */
    private const val MIN_GROUPS = 4

    private fun isUrduLetter(c: Char) = c.isLetter() && (c.code in 0x0600..0x06FF || c.code in 0x0750..0x077F || c.code in 0xFB50..0xFDFF || c.code in 0xFE70..0xFEFF)

    private fun groups(text: String): Set<String> {
        val sb = StringBuilder(" ")
        var lastSpace = true
        for (ch in text) {
            when {
                isUrduLetter(ch) -> { sb.append(normal(ch)); lastSpace = false }
                !lastSpace && (ch == ' ' || ch == '\n' || ch.isWhitespace() || ch == '\u060C' || ch == '\u06D4' || ch == ',' || ch == '.') -> { sb.append(' '); lastSpace = true }
                // anything else (Latin words, digits, marks) is skipped
            }
        }
        if (sb.last() != ' ') sb.append(' ')
        val t = sb.toString()
        if (t.length < 3) return emptySet()
        return (0..t.length - 3).map { t.substring(it, it + 3) }.toSet()
    }

    /** Spelling variants of the same letter count as one: the two kinds of ye, he and kaf. */
    private fun normal(c: Char): Char = when (c) {
        '\u0649', '\u06CC', '\u06D2', '\u06D3' -> '\u064A'      // ye forms
        '\u06C1', '\u06BE', '\u06C3', '\u0647' -> '\u0647'      // he forms
        '\u06A9' -> '\u0643'                                    // kaf
        '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627'      // alef forms
        else -> c
    }

    /** 0 to 1; 1.0 when there is too little Urdu to judge (so a check never fails on that account). */
    fun dice(a: String, b: String): Double {
        val ga = groups(a)
        val gb = groups(b)
        if (ga.size < MIN_GROUPS || gb.size < MIN_GROUPS) return 1.0
        val common = ga.count { it in gb }
        return 2.0 * common / (ga.size + gb.size)
    }
}

/** How much to trust the English, judged by [Similarity] of its back-translation. The numbers are starting points to tune on real notes. */
object TranslationCheck {
    /** Below this, translate once more, closer to the Urdu, and keep whichever comes back more alike. */
    const val TRY_AGAIN_BELOW = 0.35

    /** Below this even after the second try, the screen says the English may be off. */
    const val UNSURE_BELOW = 0.25
}
