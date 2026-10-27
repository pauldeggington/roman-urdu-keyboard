package com.paul.urdukb

/**
 * Who a sentence is about: the speaker ("I am tired") or the listener ("You are tired"). A small model often mixes
 * these up, because Urdu verbs can drop the pronoun. So the message is tagged by simple rules before it goes to the
 * model, the model is told what the tags mean, and the answer is checked against the tag (with one stricter retry).
 */
object Person {
    enum class Who { SPEAKER, LISTENER }

    private val word = Regex("[\\p{L}']+")
    private val sentenceEnd = Regex("(?<=[.!?\\u061F\\n])\\s+")

    // ---------------------------------------------------------------- English
    private val enSpeaker = setOf("i", "i'm", "im", "i've", "ive", "i'll", "i'd", "we", "we're", "we've", "we'll", "we'd")
    private val enListener = setOf("you", "you're", "youre", "you've", "you'll", "you'd", "u", "y'all")
    private val enSpeakerAny = enSpeaker + setOf("me", "my", "mine", "myself", "our", "ours", "us", "ourselves")
    private val enListenerAny = enListener + setOf("your", "yours", "ur", "yourself", "yourselves")

    // ---------------------------------------------------------------- Roman Urdu
    private val ruSpeaker = setOf("main", "mai", "hoon", "hun", "mein_main")
    private val ruListener = setOf("tum", "tu", "aap", "tm")
    private val ruSpeakerAny = ruSpeaker + setOf("mujhe", "mujh", "mujhko", "mera", "meri", "mere", "meray", "hum", "humein", "hamein", "humara", "humari", "humare", "humaray")
    private val ruListenerAny = ruListener + setOf(
        "tumhe", "tumhein", "tumhara", "tumhari", "tumhare", "tumharay", "tumse", "tumko", "aapko", "aapka", "aapki", "aapke", "aapkay", "aapse", "tujhe", "tera", "teri",
    )

    private fun tokens(s: String) = word.findAll(s.lowercase().replace('\u2019', '\'')).map { it.value }.toList()

    /** The person a sentence is about, judged by its subject: the first "I/we" or "you" in it. Null when unclear. */
    fun ofEnglish(sentence: String): Who? {
        for (t in tokens(sentence)) {
            if (t in enSpeaker) return Who.SPEAKER
            if (t in enListener) return Who.LISTENER
        }
        return null
    }

    fun ofRomanUrdu(sentence: String): Who? {
        for (t in tokens(sentence)) {
            if (t in ruSpeaker) return Who.SPEAKER
            if (t in ruListener) return Who.LISTENER
        }
        return null
    }

    private fun tagFor(style: Style, who: Who): String = when (style) {
        Style.TO_ENGLISH -> if (who == Who.SPEAKER) "(main)" else "(tum/aap)"
        else -> if (who == Who.SPEAKER) "(I)" else "(you)"
    }

    /** Puts a tag in front of every sentence whose subject is clear. */
    fun tag(text: String, style: Style): String {
        val parts = text.trim().split(sentenceEnd).filter { it.isNotBlank() }
        if (parts.isEmpty()) return text
        return parts.joinToString(" ") { sentence ->
            val who = if (style == Style.TO_ENGLISH) ofRomanUrdu(sentence) else ofEnglish(sentence)
            if (who == null) sentence else tagFor(style, who) + " " + sentence
        }
    }

    /**
     * The one person the whole message is about, or null. Null when it mixes both ("I love you", "how about you?")
     * or neither is clear, because then a translation may rightly talk about both and cannot be checked.
     */
    fun overall(text: String, style: Style): Who? {
        val roman = style == Style.TO_ENGLISH
        val found = text.trim().split(sentenceEnd).filter { it.isNotBlank() }
            .mapNotNull { if (roman) ofRomanUrdu(it) else ofEnglish(it) }.toSet()
        if (found.size != 1) return null
        val who = found.first()
        val t = tokens(text).toSet()
        val otherMentioned = if (roman) {
            t.any { it in (if (who == Who.SPEAKER) ruListenerAny else ruSpeakerAny) }
        } else {
            t.any { it in (if (who == Who.SPEAKER) enListenerAny else enSpeakerAny) }
        }
        return if (otherMentioned) null else who
    }

    private val leftoverTag = Regex("\\((?:I|you|main|tum/aap)\\)\\s*", RegexOption.IGNORE_CASE)

    /** The model should never write the tags back; remove them if it does. */
    fun stripTags(s: String) = s.replace(leftoverTag, "").trim()

    /** Does the translation talk about the right person? Only clear contradictions count as wrong. */
    fun consistent(output: String, who: Who, style: Style): Boolean {
        val t = tokens(output).toSet()
        return if (style == Style.TO_ENGLISH) {
            val hasS = t.any { it in enSpeakerAny }
            val hasL = t.any { it in enListenerAny }
            when (who) { Who.SPEAKER -> hasS || !hasL; Who.LISTENER -> hasL || !hasS }
        } else {
            val hasS = t.any { it in ruSpeakerAny }
            val hasL = t.any { it in ruListenerAny } || "ho" in t
            when (who) { Who.SPEAKER -> hasS || !hasL; Who.LISTENER -> hasL || !hasS }
        }
    }

    /** What the system prompt says about the tags. */
    fun tagRules(style: Style): String = if (style == Style.TO_ENGLISH) {
        "\n- A tag in front of a sentence says who it is about: (main) the speaker, so write I/me/my; (tum/aap) the listener, so write you/your. Never write the tag."
    } else {
        "\n- A tag in front of a sentence says who it is about: (I) the speaker (main, mujhe, mera, hoon); (you) the listener (tum/aap, tumhara/aapka, ho/hain). Follow it and never write the tag."
    }

    /** Extra line for the second try, when the first answer talked about the wrong person. */
    fun retryNote(who: Who, style: Style): String = if (style == Style.TO_ENGLISH) {
        if (who == Who.SPEAKER) "\n- IMPORTANT: this text is about the speaker. Write it with \"I\", \"me\" and \"my\", not \"you\"."
        else "\n- IMPORTANT: this text is about the person being spoken to. Write it with \"you\" and \"your\", not \"I\"."
    } else {
        if (who == Who.SPEAKER) "\n- IMPORTANT: this text is about the speaker (\"I\"). Write it with main, mujhe, mera and \"hoon\" forms, never tum or aap as the subject."
        else "\n- IMPORTANT: this text is about the person being spoken to (\"you\"). Write it with tum/aap, tumhara/aapka and \"ho/hain\" forms, never main or \"hoon\" as the subject. Do not answer it, translate it."
    }
}
