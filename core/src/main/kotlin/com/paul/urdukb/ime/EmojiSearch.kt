package com.paul.urdukb.ime

/**
 * Finding an emoji from what you type. Matches the emoji's name word by word, understands everyday words
 * ("sad", "laugh") and Roman Urdu ones ("khushi", "pyaar", "shukriya"), and forgives a typo in longer words.
 * Plain Kotlin with no Android calls, so it is easy to test.
 */
object EmojiSearch {
    private val synonyms: Map<String, List<String>> = mapOf(
        // everyday English
        "lol" to listOf("joy", "rolling on the floor"), "haha" to listOf("joy", "grinning", "rolling on the floor"),
        "laugh" to listOf("joy", "grinning", "rolling on the floor"), "love" to listOf("heart", "kiss", "love"),
        "sad" to listOf("cry", "sad", "pensive", "disappointed"), "happy" to listOf("smil", "grin"),
        "angry" to listOf("angry", "rage", "pouting"), "mad" to listOf("angry", "rage", "pouting"),
        "ok" to listOf("ok hand", "thumbs up"), "okay" to listOf("ok hand", "thumbs up"), "thanks" to listOf("folded hands"),
        "thank" to listOf("folded hands"), "pray" to listOf("folded hands"), "cool" to listOf("sunglasses"),
        "yes" to listOf("thumbs up", "check mark"), "no" to listOf("thumbs down", "cross mark", "prohibited"),
        "party" to listOf("party", "confetti", "balloon", "tada"), "sleep" to listOf("sleep", "zzz"),
        "tired" to listOf("sleep", "yawning", "weary", "tired"), "food" to listOf("pizza", "hamburger", "food", "rice", "curry"),
        "tea" to listOf("teacup", "hot beverage"), "smile" to listOf("smil", "grin"),
        "wow" to listOf("astonished", "star-struck", "exploding head"), "bye" to listOf("waving hand"),
        "hi" to listOf("waving hand"), "hello" to listOf("waving hand"), "think" to listOf("thinking"),
        "money" to listOf("money", "dollar", "coin"), "fire" to listOf("fire"), "birthday" to listOf("birthday", "cake", "party"),
        "congrats" to listOf("party", "clapping", "confetti", "tada"), "sorry" to listOf("pleading", "pensive", "folded hands"),
        "kiss" to listOf("kiss"), "hug" to listOf("hugging"), "cry" to listOf("cry", "sob"), "scared" to listOf("fearful", "scream"),
        "sick" to listOf("sneezing", "nauseated", "mask", "thermometer"), "work" to listOf("laptop", "briefcase", "office"),
        "car" to listOf("automobile", "car", "taxi"), "home" to listOf("house", "home"), "phone" to listOf("mobile phone", "telephone"),
        // Roman Urdu
        "khushi" to listOf("smil", "grin", "party"), "khush" to listOf("smil", "grin"), "pyaar" to listOf("heart", "kiss", "love"),
        "pyar" to listOf("heart", "kiss", "love"), "mohabbat" to listOf("heart", "kiss", "love"), "dil" to listOf("heart"),
        "shukriya" to listOf("folded hands"), "shukria" to listOf("folded hands"), "dua" to listOf("folded hands"),
        "mubarak" to listOf("party", "confetti", "tada", "clapping"), "udaas" to listOf("cry", "sad", "pensive", "disappointed"),
        "hansi" to listOf("joy", "grinning"), "hasna" to listOf("joy", "grinning"), "gussa" to listOf("angry", "rage", "pouting"),
        "neend" to listOf("sleep", "zzz"), "khana" to listOf("pizza", "food", "curry", "rice", "hamburger"),
        "chai" to listOf("teacup", "hot beverage"), "paisa" to listOf("money", "dollar", "coin"), "paise" to listOf("money", "dollar", "coin"),
        "aag" to listOf("fire"), "chand" to listOf("crescent", "moon"), "suraj" to listOf("sun"), "kitab" to listOf("book"),
        "dost" to listOf("hugging", "handshake", "people"), "salam" to listOf("waving hand", "folded hands"),
        "masjid" to listOf("mosque"), "kaaba" to listOf("kaaba"), "eid" to listOf("crescent", "mosque", "star and crescent"),
        "ramzan" to listOf("crescent", "mosque", "star and crescent"), "ramadan" to listOf("crescent", "mosque", "star and crescent"),
        "shabash" to listOf("clapping", "thumbs up"), "wah" to listOf("clapping", "star-struck"), "afsos" to listOf("pensive", "disappointed", "cry"),
        "theek" to listOf("ok hand", "thumbs up"), "aankh" to listOf("eye"), "gaari" to listOf("automobile", "car"),
        "ghar" to listOf("house", "home"), "kaam" to listOf("laptop", "briefcase"), "bimar" to listOf("sneezing", "nauseated", "mask", "thermometer"),
        "darr" to listOf("fearful", "scream"), "hairat" to listOf("astonished", "star-struck"), "sharam" to listOf("flushed", "smiling face with smiling eyes"),
        "bohat" to listOf("hundred"), "cricket" to listOf("cricket"), "baarish" to listOf("rain", "cloud"),
    )

    /** How well [name] fits [words]: 0 is best, null is no match. [fuzzy] lets a longer word be one typo away. */
    fun score(name: String, words: List<String>, fuzzy: Boolean = false): Int? {
        var total = 0
        for (w in words) {
            val options = synonyms[w] ?: listOf(w)
            var best = -1
            for (o in options) {
                val i = name.indexOf(o)
                if (i < 0) continue
                val s = if (i == 0) 0 else if (name[i - 1] == ' ') 1 else 2
                if (best < 0 || s < best) best = s
            }
            if (best < 0 && fuzzy && w.length >= 4) {
                // a typo in a longer word: compare with each word of the name
                for (nw in name.split(' ', '-', ':')) {
                    if (nw.length >= 3 && closeTo(w, nw)) { best = 3; break }
                }
            }
            if (best < 0) return null
            total += best
        }
        return total
    }

    /** True when the two words are at most one edit apart (insert, delete, change or swap two neighbouring letters). */
    fun closeTo(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            var first = -1
            var diffs = 0
            for (i in a.indices) if (a[i] != b[i]) { if (first < 0) first = i; diffs++ }
            if (diffs == 1) return true
            return diffs == 2 && first + 1 < a.length && a[first] == b[first + 1] && a[first + 1] == b[first]
        }
        val (s, l) = if (a.length < b.length) a to b else b to a
        var i = 0
        var j = 0
        var skipped = false
        while (i < s.length && j < l.length) {
            if (s[i] == l[j]) { i++; j++ } else { if (skipped) return false; skipped = true; j++ }
        }
        return true
    }

    fun words(query: String) = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
