package com.paul.urdukb.ime

/**
 * Word suggestions while typing: finishes the word you are typing ("th" -> the, that, this) and offers a fix when it
 * is not a word ("teh" -> the). Plain Kotlin, so it is tested without Android.
 *
 * Words come with a frequency score (about 100 times the Zipf value: 7.7 for "the", 3 for a rare word). Words the
 * person types a lot ([personal]) are ranked high, so the keyboard learns names and Roman Urdu spellings.
 */
class WordSuggester(words: List<Pair<String, Int>>) {

    private val sorted: Array<String>
    private val zipf: IntArray
    private val exact = HashMap<String, Int>()
    /** For fixing typos: word indices grouped by length of the word without apostrophes. */
    private val byLength: Array<IntArray>
    private val plain: Array<String>

    init {
        val list = words.filter { it.first.isNotEmpty() }.sortedBy { it.first }
        sorted = Array(list.size) { list[it].first }
        zipf = IntArray(list.size) { list[it].second }
        plain = Array(list.size) { sorted[it].replace("'", "") }
        for (i in sorted.indices) exact[sorted[i]] = zipf[i]
        val maxLen = (plain.maxOfOrNull { it.length } ?: 0) + 1
        val buckets = Array(maxLen + 1) { ArrayList<Int>() }
        for (i in plain.indices) buckets[plain[i].length].add(i)
        byLength = Array(buckets.size) { buckets[it].toIntArray() }
    }

    fun isWord(word: String) = exact.containsKey(word.lowercase())

    private class Cand(val word: String, val score: Int)

    /** First index in [sorted] that is >= [prefix]. */
    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Up to [max] words for what has been typed so far, best first, all lower case.
     * [personal] is how often the person typed each word (their own words and names).
     *
     * The word list comes from real writing, so it also contains common misspellings ("teh", "recieve", "dont").
     * A word that is in the list is therefore still offered a fix, when another word one or two typos away is far
     * more common. A real word such as "form" (against "from") is left alone.
     */
    fun suggest(typedRaw: String, personal: Map<String, Int> = emptyMap(), max: Int = 3): List<String> {
        val typed = typedRaw.lowercase()
        if (typed.length < 2 || typed.any { !(it.isLetter() || it == '\'') }) return emptyList()
        val found = HashMap<String, Int>()
        val fixes = HashMap<String, Int>()

        fun offer(word: String, score: Int) {
            if (word == typed) return
            val old = found[word]
            if (old == null || score > old) found[word] = score
        }

        // 1. finishing the word
        var i = lowerBound(typed)
        var seen = 0
        val completions = ArrayList<Cand>()
        while (i < sorted.size && sorted[i].startsWith(typed) && seen < 6000) {
            if (sorted[i] != typed) {
                completions += Cand(sorted[i], zipf[i] + 30)
                if (completions.size > 400) {   // keep the list small: only the commonest matter
                    completions.sortByDescending { it.score }
                    while (completions.size > 12) completions.removeAt(completions.size - 1)
                }
            }
            i++; seen++
        }
        completions.sortByDescending { it.score }
        for (c in completions.take(8)) offer(c.word, c.score)
        for ((w, n) in personal) if (w.length > typed.length && w.startsWith(typed)) offer(w, PERSONAL_BASE + minOf(n, 20) * 6)

        // 2. fixing a typo (never for a word the person uses themselves)
        val ownWord = personal.containsKey(typed)
        val inList = exact.containsKey(typed)
        val typedZipf = exact[typed] ?: -1
        if (!ownWord && typed.length == 2 && '\'' !in typed && typed.length < byLength.size) {
            // two letters are too few to guess a typo from, but "im" -> "i'm" is always worth offering
            for (idx in byLength[2]) if (plain[idx] == typed && sorted[idx] != typed) { offer(sorted[idx], zipf[idx] + 250); fixes[sorted[idx]] = zipf[idx] + 250 }
        }
        if (!ownWord && typed.length >= 3) {
            val flat = typed.replace("'", "")
            val allowed = if (flat.length >= 7) 2 else 1
            val margin = if (flat.length <= 3) 250 else 200
            for (len in maxOf(1, flat.length - allowed)..minOf(byLength.size - 1, flat.length + allowed)) {
                for (idx in byLength[len]) {
                    if (sorted[idx] == typed) continue
                    val d = distanceWithin(flat, plain[idx], allowed)
                    if (d !in 0..allowed) continue
                    // "dont" -> "don't": the same letters with an apostrophe is always worth offering
                    val contraction = d == 0
                    if (inList && !contraction && zipf[idx] < typedZipf + margin) continue
                    val score = zipf[idx] - 120 * d + (if (contraction) 250 else 0)
                    offer(sorted[idx], score)
                    fixes[sorted[idx]] = score
                }
            }
            if (!inList) {
                for ((w, n) in personal) {
                    val d = distanceWithin(flat, w, allowed)
                    if (d in 1..allowed) offer(w, PERSONAL_BASE + minOf(n, 20) * 6 - 120 * d)
                }
            }
        }

        val ranked = found.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
        // the word as typed stays on the strip when it is a word the person uses or a common one, so a tap never
        // changes a correct word by accident (a rare list entry like "th" is not given a slot)
        if (ownWord || (inList && typedZipf >= 400)) {
            val bestFix = fixes.maxByOrNull { it.value }?.key
            return if (bestFix != null) (listOf(bestFix, typed) + ranked.filter { it != bestFix }).take(max)
            else (listOf(typed) + ranked).take(max)
        }
        return ranked.take(max)
    }

    private class Fix(val word: String, val score: Int, val contraction: Boolean)

    /**
     * The one word [typedRaw] should be silently replaced with when it is followed by a space, or null to leave it.
     * Careful on purpose: only clear typos ("teh", "recieve", "dont"), where one candidate is far more common than
     * the typed word and clearly ahead of any other. Words the person uses themselves are never changed.
     */
    fun autocorrect(typedRaw: String, personal: Map<String, Int> = emptyMap()): String? {
        val typed = typedRaw.lowercase()
        if (typed.length < 2 || typed.any { !(it.isLetter() || it == '\'') } || '\'' in typed) return null
        if (typed.length == 2 && typed != "im") return null
        if (personal.containsKey(typed) || typed in NOT_CONTRACTIONS) return null
        SHORTHAND[typed]?.let { return it }
        val typedZipf = exact[typed] ?: -1
        val inList = typedZipf >= 320          // below that the list entry is a common misspelling, not a word
        // a word that is clearly in everyday use is not a typo; the list also holds some misspellings, which are rare
        if (inList && typedZipf >= 520) {
            // except an apostrophe-less contraction ("dont", "cant"), handled below
            if (!hasContraction(typed)) return null
        }
        val fixes = ArrayList<Fix>()
        val allowed = if (typed.length >= 7) 2 else 1
        for (len in maxOf(1, typed.length - allowed)..minOf(byLength.size - 1, typed.length + allowed)) {
            for (idx in byLength[len]) {
                if (sorted[idx] == typed) continue
                val d = distanceWithin(typed, plain[idx], allowed)
                if (d !in 0..allowed) continue
                val contraction = d == 0
                if (contraction && typed.length == 2 && typed != "im") continue
                val margin = if (contraction) 100 else if (typed.length <= 3) 300 else 250
                if (inList && zipf[idx] < typedZipf + margin) continue
                if (!inList && zipf[idx] < 420) continue          // only correct towards a word in everyday use
                // a dropped letter is the commonest slip ("sory", "helo"); a deleted one is rarer and riskier ("hows" is not "how")
                val dropped = plain[idx].length > typed.length && isSubsequence(typed, plain[idx])
                val shorter = plain[idx].length < typed.length && !contraction
                fixes += Fix(sorted[idx], zipf[idx] - 120 * d + (if (contraction) 250 else 0) + (if (dropped) 80 else 0) - (if (shorter) 90 else 0), contraction)
            }
        }
        if (fixes.isEmpty()) return null
        fixes.sortByDescending { it.score }
        val best = fixes[0]
        if (fixes.size > 1 && best.score - fixes[1].score < 40) return null   // two good answers: do not guess
        return best.word
    }

    private fun isSubsequence(small: String, big: String): Boolean {
        var i = 0
        for (c in big) if (i < small.length && small[i] == c) i++
        return i == small.length
    }

    private fun hasContraction(typed: String): Boolean {
        val idx = lowerBound(typed.take(1))
        var i = idx
        while (i < sorted.size && sorted[i].startsWith(typed.take(1))) {
            if (sorted[i].length == typed.length + 1 && plain[i] == typed && sorted[i] != typed) return true
            i++
        }
        return false
    }

    /** Edit distance (insert, delete, change, swap two letters) if it is at most [limit], otherwise -1. */
    private fun distanceWithin(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return -1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (x in 1..a.length) {
            cur[0] = x
            var rowMin = cur[0]
            for (y in 1..b.length) {
                val cost = if (a[x - 1] == b[y - 1]) 0 else 1
                var v = minOf(prev[y] + 1, cur[y - 1] + 1, prev[y - 1] + cost)
                if (x > 1 && y > 1 && a[x - 1] == b[y - 2] && a[x - 2] == b[y - 1]) v = minOf(v, prev2[y - 2] + 1)
                cur[y] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return -1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        val d = prev[b.length]
        return if (d <= limit) d else -1
    }

    companion object {
        /** Chat shorthand that is plainly one thing. */
        private val SHORTHAND = mapOf("thanx" to "thanks", "thankyou" to "thank you", "alot" to "a lot", "tonite" to "tonight", "gud" to "good")

        private const val PERSONAL_BASE = 600

        /** Words that look like a contraction without the apostrophe but are real words: never auto-corrected. */
        private val NOT_CONTRACTIONS = setOf("its", "were", "well", "ill", "id", "hell", "shell", "wed", "shed", "hed")

        /** "word<TAB>score" lines, as in the glide word list. */
        fun readWords(lines: Sequence<String>): List<Pair<String, Int>> {
            val out = ArrayList<Pair<String, Int>>(50_000)
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val score = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                out += line.substring(0, tab) to score
            }
            return out
        }

        /** Gives a suggestion the capitals of what was typed: Th -> The, TH -> THE, th -> the. */
        fun matchCase(typed: String, suggestion: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> suggestion.uppercase()
            typed.isNotEmpty() && typed[0].isUpperCase() -> suggestion.replaceFirstChar { it.uppercaseChar() }
            suggestion == "i" || suggestion.startsWith("i'") -> "I" + suggestion.substring(1)
            else -> suggestion
        }
    }
}
