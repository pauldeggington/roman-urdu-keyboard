package com.paul.urdukb

/**
 * Guesses the tone of an English sentence from the words around it: who it is to (an elder, a partner), how it
 * feels (joking, longing). Plain and cautious on purpose: when nothing clear points one way the answer is the
 * person's usual tone, never a surprise. The person can always pick a tone by hand.
 */
object ToneGuess {

    private val WORD = Regex("[\\p{L}']+")

    private val ELDERS = setOf(
        "sir", "madam", "madame", "ma'am", "maam", "respected", "sahab", "sahib", "janab", "ammi", "amma", "abbu", "abba", "abu",
        "baba", "dada", "dadi", "nana", "nani", "uncle", "aunty", "auntie", "grandfather", "grandmother", "grandpa", "grandma",
        "teacher", "professor", "principal", "ustad", "elder", "elders", "salaam", "salam", "assalam", "inshallah", "mashallah",
        "blessings", "dua", "duas",
    )

    private val LOVE = setOf(
        "love", "darling", "honey", "sweetheart", "babe", "baby", "jaan", "sweetie", "hug", "kiss", "kisses", "hubby", "wifey",
        "beloved", "cutie",
    )
    private val LOVE_PHRASES = listOf("miss you", "love you", "my love", "my dear", "my heart", "i need you", "think of you", "thinking of you")

    /** Clear signs of a joke or banter. */
    private val FUNNY_STRONG = setOf("haha", "hahaha", "lol", "lmao", "rofl", "joke", "joking", "kidding", "hilarious", "funny", "roast")
    private val FUNNY_PHRASES = listOf("just kidding", "not lazy", "miracles do happen", "don't tell", "send help", "i'm on a diet", "energy-saving")
    /** Words that go with banter but also appear in ordinary talk: it takes several together. */
    private val FUNNY_WEAK = setOf(
        "silly", "idiot", "dummy", "drama", "dramatic", "lazy", "diet", "snack", "biryani", "pizza", "boss", "monday", "wallet", "broke",
        "nap", "sleepy",
    )

    private val POETIC = setOf(
        "moon", "moonlight", "stars", "starlit", "sunrise", "sunset", "dawn", "dusk", "twilight", "silence", "soul", "dreams",
        "dream", "longing", "yearning", "wound", "wounds", "tears", "heartbeat", "eternity", "destiny", "fate", "horizon", "breeze",
        "shadow", "shadows", "whisper", "whispers", "poem", "verse", "ghazal", "shayari", "journey", "ocean", "rain", "heartbreak",
        "candle", "lamp", "darkness", "memory", "memories",
    )

    /** Unmistakable asks for a poem. */
    private val POETIC_ASK = listOf("poem", "ghazal", "shayari", "couplet", "verse")

    private fun emojiScore(text: String, vararg marks: String) = marks.count { text.contains(it) }

    /**
     * The tone to use for [text]. [base] is the person's usual register (Casual or Formal). Someone who chose Formal
     * (aap) keeps it unless the sentence is clearly for an elder or is poetry: a joke or a pet name in "aap" would read oddly.
     */
    fun guess(text: String, base: Style): Style {
        val lower = text.lowercase()
        val words = WORD.findAll(lower).map { it.value }.toList()
        if (words.isEmpty()) return base
        val set = words.toHashSet()

        val elder = ELDERS.count { it in set }
        var love = LOVE.count { it in set } + LOVE_PHRASES.count { lower.contains(it) } + emojiScore(text, "❤", "😘", "🥰", "💕", "💖", "💗")
        // a clear sign of a joke counts three, an everyday word one; three is a joke
        val funny = 3 * (FUNNY_STRONG.count { it in set } + FUNNY_PHRASES.count { lower.contains(it) } + emojiScore(text, "😂", "🤣", "😜", "😝", "😆")) +
            FUNNY_WEAK.count { it in set }
        val poeticWords = POETIC.count { it in set }
        val asked = POETIC_ASK.any { it in set }
        val poetic = if (asked) 3 else poeticWords

        // "love" in a sentence about food or things is not about a person: ask for a second cue
        if (love == 1 && set.any { it == "pizza" || it == "biryani" || it == "movie" || it == "movies" || it == "music" || it == "coffee" }) love = 0

        val formalBase = base == Style.FORMAL
        return when {
            elder >= 1 && love < 2 -> Style.RESPECTFUL
            formalBase && poetic >= 2 -> Style.POETIC
            formalBase -> Style.FORMAL
            love >= 1 && poetic >= 2 -> Style.POETIC
            love >= 1 -> Style.AFFECTIONATE
            funny >= 3 -> Style.FUNNY
            poetic >= 2 -> Style.POETIC
            else -> base
        }
    }
}
