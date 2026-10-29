package com.paul.urdukb

/**
 * A message that is only a word or a short phrase ("friend", "good morning") must come back as that word or phrase, not as
 * a sentence or a question. A small model shown mostly full sentences will otherwise imitate them ("Aap dost hain?").
 * Three guards: a rule in the prompt, a group of word-and-phrase examples shown for short messages, and a check on the
 * answer with one stricter retry.
 */
object ShortInput {

    /** The heading of the word-and-phrase examples in each example bank. */
    const val GROUP = "words and phrases"

    const val PROMPT = "\n- If the message is only a word or a short phrase, not a full sentence, translate just that word or phrase. Never turn it into a sentence or a question, never add aap or tum, and never answer it. \"Friend\" is simply \"Dost\"."

    const val RETRY_NOTE = "\n- IMPORTANT: this message is only a word or a short phrase. Give just its translation, one to three words. No question, no sentence, no aap or tum."

    private val WORD = Regex("[\\p{L}']+")
    private val PEOPLE = setOf("i", "i'm", "me", "my", "we", "you", "your", "you're", "u", "ur", "our", "us", "he", "she", "they", "it's", "is", "are", "am", "do", "does", "can", "could", "will", "would", "should")
    private val QUESTION_WORDS = setOf("what", "why", "who", "whom", "whose", "how", "where", "when", "which")
    private val ASKING_OPENERS = setOf("kya", "kia", "kaun", "kon", "kyun", "kab", "kahan", "kaisay", "aap", "tum", "tu")

    private fun tokens(s: String) = WORD.findAll(s.lowercase().replace('’', '\'')).map { it.value }.toList()

    /** True for a bare word or short phrase: up to three words, no sentence marks, no pronoun or question word. */
    fun isShort(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.any { it == '.' || it == '?' || it == '!' || it == '\n' || it == '؟' }) return false
        val w = tokens(t)
        if (w.isEmpty() || w.size > 3) return false
        return w.none { it in PEOPLE || it in QUESTION_WORDS }
    }

    /** Did the model turn a short message into a question or a long answer? */
    fun wrong(input: String, output: String): Boolean {
        if (!isShort(input)) return false
        val out = output.trim()
        if (out.contains('?') || out.contains('؟')) return true
        val o = tokens(out)
        if (o.isEmpty()) return false
        if (o.first() in ASKING_OPENERS) return true
        return o.size > maxOf(3, tokens(input).size * 2 + 1)
    }
}
