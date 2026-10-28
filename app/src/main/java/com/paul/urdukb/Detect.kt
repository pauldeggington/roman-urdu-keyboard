package com.paul.urdukb

/** Guesses whether Latin-script text is Roman Urdu or English. Used by the Auto direction. */
object Detect {

    /** Common Roman Urdu words, in the spellings people actually type. Kept to words that are rare or absent in English. */
    private val URDU = setOf(
        "hai", "hain", "hay", "ho", "hon", "hoon", "hun", "tha", "thi", "thay", "the", "thee",
        "nahi", "nahin", "nai", "na", "naa", "kya", "kia", "kyun", "kyu", "kyon", "kaise", "kaisay", "kaisa", "kaisi",
        "kahan", "kaha", "kab", "kaun", "kon", "kitna", "kitnay", "kitne", "kitni", "kitna",
        "mein", "main", "mai", "mujhe", "mujhay", "mujh", "meri", "mera", "mere", "meray", "hum", "humein", "humara", "hamara", "hamari",
        "tum", "tumhe", "tumhein", "tumhara", "tumhari", "tumhare", "tumharay", "tu", "tera", "teri", "tere", "tujhe",
        "aap", "aapka", "aapki", "aapke", "aapko", "aapkay",
        "yeh", "ye", "woh", "wo", "yahan", "yaha", "wahan", "waha", "iss", "uss", "isko", "usko", "unka", "inka",
        "ka", "ki", "ke", "kay", "ko", "se", "sy", "par", "pe", "tak", "bhi", "hi", "to", "toh", "lekin", "magar", "aur", "ya", "agar",
        "abhi", "ab", "phir", "pehle", "pehlay", "baad", "kal", "aaj", "raat", "subah", "shaam", "dopahar", "jaldi", "der",
        "acha", "accha", "achi", "achay", "theek", "thik", "bohat", "bahut", "boht", "bht", "bohot", "kaafi", "zyada", "zada", "thora", "thoda", "thori",
        "karna", "karni", "karo", "karein", "karta", "karti", "kartay", "karte", "karunga", "karungi", "kiya", "kia", "kiye", "kare", "karen",
        "jana", "jaana", "jao", "jaye", "jayen", "gaya", "gayi", "gaye", "gaii", "ja", "jaa",
        "aana", "aao", "aaye", "aayi", "aya", "ayi", "aa", "rahay", "rahe", "raha", "rahi", "rahi",
        "chahta", "chahti", "chahtay", "chahte", "chahiye", "chahiay", "chaye", "chahye",
        "dena", "dedo", "do", "diya", "diye", "dijiye", "dikhao", "batao", "batana", "bataiye", "bata", "dekho", "dekhna", "dekh", "suno", "sunna",
        "milna", "mila", "mili", "mile", "milte", "lena", "lo", "liya", "lia", "le", "lay", "bolo", "bolna", "bol",
        "sakta", "sakti", "saktay", "sakte", "sakna", "sako", "sakein",
        "pyaar", "pyar", "dost", "yaar", "yar", "bhai", "behen", "bhaiya", "ammi", "abbu", "baji", "shukriya", "maazrat", "meherbani", "inshallah", "mashallah", "walaikum", "salam", "khuda", "allah",
        "kuch", "kuchh", "sab", "sabhi", "koi", "kisi", "har", "wala", "wali", "walay", "wale", "jaisa", "jaise", "jese", "waisa", "waise", "aisa", "aise", "aisay",
        "ghar", "kaam", "paisa", "paise", "khana", "chai", "pani", "log", "baat", "waqt", "din", "saal", "naam", "pagal", "bas", "chalo", "chal", "arre", "oye", "haan", "han", "ji", "jee", "nhi", "nh", "kr", "krna", "krta", "krti", "hy", "h",
    )

    /** English function words. If these show up, it is probably English even with a stray Urdu word. */
    private val ENGLISH = setOf(
        "the", "is", "are", "was", "were", "and", "of", "in", "on", "for", "with", "that", "this", "it", "you", "your", "i", "my", "we", "he", "she", "they",
        "have", "has", "had", "be", "been", "will", "would", "can", "could", "should", "do", "does", "did", "not", "but", "so", "at", "by", "from", "what", "when", "where",
        "how", "why", "who", "please", "thanks", "thank", "hello", "hi", "yes", "no", "ok", "okay", "just", "going", "want", "need", "know", "think", "get", "got",
    )

    // Words that are valid in both languages. They count for neither side.
    private val SHARED = setOf("to", "do", "me", "he", "the", "a", "an", "in", "on", "at", "or", "so", "no", "na", "ho", "hi", "ha", "ye", "ya", "tu", "pe", "be", "us", "it", "is", "as", "ok", "i")

    private val NOT_LATIN = Regex("[^a-z']+")

    fun isEnglishWord(word: String): Boolean = word in ENGLISH

    enum class Language { ROMAN_URDU, ENGLISH }

    fun guess(text: String, isKnownVariant: (String) -> Boolean = { false }): Language {
        // Any Urdu/Arabic script at all: treat as Urdu script, handled by the caller as RU to EN.
        if (text.any { it in '؀'..'ۿ' }) return Language.ROMAN_URDU

        val words = text.lowercase().split(NOT_LATIN).filter { it.isNotEmpty() }
        if (words.isEmpty()) return Language.ENGLISH

        var urdu = 0
        var english = 0
        for (w in words) {
            val shared = w in SHARED
            if (w in URDU && !shared) urdu++
            if (w in ENGLISH && !shared) english++
        }
        // Roman Urdu spellings the variant data knows, beyond the built-in list. Needs two of them and no English words.
        var extra = 0
        for (w in words) if (w.length >= 4 && w !in URDU && w !in ENGLISH && isKnownVariant(w)) extra++
        // Urdu words are weighted a little higher: a single "hai" or "kya" is a strong signal.
        return if ((urdu >= 1 && urdu * 1.5 >= english) || (urdu == 0 && extra >= 2 && english == 0)) Language.ROMAN_URDU
        else Language.ENGLISH
    }
}
