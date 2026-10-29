package com.paul.urdukb

/**
 * The spelling glossary: "word -> preferred spelling".
 * It is used twice: the model is told the preferred spellings, and the answer is then checked word by word,
 * so a wrong spelling that slips through (bada instead of barha) is fixed for sure.
 */
object Glossary {

    private val SEP = Regex("\\s*(?:->|\u2192|=>|=|\\|)\\s*")

    fun parse(text: String): List<Pair<String, String>> =
        text.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            val parts = line.split(SEP, limit = 2)
            if (parts.size < 2) return@mapNotNull null
            val from = parts[0].trim()
            val to = parts[1].trim()
            if (from.isEmpty() || to.isEmpty() || from.equals(to, ignoreCase = true)) null else from to to
        }

    /** One more bullet for the model's rules. Empty if there is nothing to say. */
    fun promptBlock(pairs: List<Pair<String, String>>): String {
        if (pairs.isEmpty()) return ""
        val list = pairs.take(60).joinToString(", ") { "${it.first} \u2192 ${it.second}" }
        return "\n- Preferred spellings: write the word on the right instead of the one on the left: $list."
    }

    private val WORD = Regex("[\\p{L}']+")

    /** The glossary turned into lookups. Built once per glossary text, not per translation. */
    class Prepared(
        val pairs: List<Pair<String, String>>,
        val words: Map<String, String>,
        val phrases: Map<String, String>,
        val phraseRe: Regex?,
    )

    /** Turns the glossary into lookups. Do this once and reuse the result for every translation. */
    fun prepare(pairs: List<Pair<String, String>>, cluster: (String) -> List<String> = { emptyList() }): Prepared {
        val targets = pairs.map { it.second.lowercase() }.toSet()
        val map = HashMap<String, String>()
        for ((from, to) in pairs) {
            for (form in cluster(from)) {
                val f = form.lowercase()
                if (f != to.lowercase() && f !in targets) map.putIfAbsent(f, to)
            }
        }
        for ((from, to) in pairs) map[from.lowercase()] = to
        // single words are looked up word by word; entries with spaces or punctuation are matched as phrases
        val words = HashMap<String, String>()
        val phrases = HashMap<String, String>()
        for ((k, v) in map) if (k.all { it.isLetter() || it == '\'' }) words[k] = v else phrases[k] = v
        val re = if (phrases.isEmpty()) null else {
            val keys = phrases.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("(?<![\\p{L}'])(?:$keys)(?![\\p{L}'])", RegexOption.IGNORE_CASE)
        }
        return Prepared(pairs, words, phrases, re)
    }

    /**
     * [cluster] gives other spellings of a word (from the spelling-variant data), so a glossary entry like
     * "kaise -> kaisay" also fixes kasay, kaysy and kesay. Your own entries always win over those.
     */
    fun apply(
        pairs: List<Pair<String, String>>,
        text: String,
        cluster: (String) -> List<String> = { emptyList() },
    ): String {
        if (pairs.isEmpty() || text.isEmpty()) return text
        return apply(prepare(pairs, cluster), text)
    }

    fun apply(c: Prepared, text: String): String {
        if (c.pairs.isEmpty() || text.isEmpty()) return text
        var out = text
        c.phraseRe?.let { re -> out = re.replace(out) { m -> matchCase(m.value, c.phrases[m.value.lowercase()] ?: m.value) } }
        if (c.words.isEmpty()) return out
        return WORD.replace(out) { m ->
            val replacement = c.words[m.value.lowercase()]
            if (replacement == null) m.value else matchCase(m.value, replacement)
        }
    }

    private fun matchCase(source: String, replacement: String): String = when {
        source.length > 1 && source.all { it.isUpperCase() } -> replacement.uppercase()
        source.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }
        else -> replacement
    }
}

/**
 * Expands texting shorthand in Roman Urdu input (kr, nhi, tm, bht ...) to full spellings before the model reads it.
 * Only unambiguous forms are listed; the example bank covers the rest.
 */
object Shorthand {

    private val MAP: Map<String, String> = mapOf(
        "kr" to "kar", "krna" to "karna", "krta" to "karta", "krti" to "karti", "krte" to "karte", "kro" to "karo",
        "krde" to "karde", "krdo" to "kardo", "krwa" to "karwa", "krun" to "karun", "krunga" to "karunga",
        "rha" to "raha", "rhi" to "rahi", "rhe" to "rahe", "rhy" to "rahay", "rhay" to "rahay",
        "nhi" to "nahi", "nhin" to "nahin", "nahe" to "nahi",
        "hy" to "hai", "hyn" to "hain",
        "ap" to "aap", "apko" to "aapko", "apka" to "aapka", "apki" to "aapki", "apke" to "aapke",
        "tm" to "tum", "tmhe" to "tumhe", "tmhein" to "tumhein", "tmhara" to "tumhara", "tmhari" to "tumhari", "tmhare" to "tumhare",
        "mjy" to "mujhe", "mje" to "mujhe", "mjhe" to "mujhe", "mujy" to "mujhe", "mjhy" to "mujhe",
        "kb" to "kab", "ky" to "kya",
        "pta" to "pata", "thk" to "theek", "thik" to "theek",
        "bht" to "bohat", "bhot" to "bohat", "boht" to "bohat", "bohot" to "bohat", "bhut" to "bohat",
        "yar" to "yaar", "yr" to "yaar", "bs" to "bas",
        "gya" to "gaya", "gye" to "gaye", "gyi" to "gayi", "gae" to "gaye",
        "plz" to "please", "pls" to "please", "thnx" to "thanks", "thx" to "thanks",
    )

    private val REGEX: Regex = Regex(
        "(?<![\\p{L}'])(?:" + MAP.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}'])",
        RegexOption.IGNORE_CASE,
    )

    fun expand(text: String): String =
        REGEX.replace(text) { m ->
            val full = MAP[m.value.lowercase()] ?: return@replace m.value
            if (m.value.first().isUpperCase()) full.replaceFirstChar { it.uppercase() } else full
        }
}
