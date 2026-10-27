package com.paul.urdukb

import android.content.Context

/**
 * Who "I" is in the translation. Without a clear first person, Urdu plural forms
 * ("thak gaye hain", "nikal rahay hain") read as if they were about "you", so the
 * speaker is always written with "main" (or "hum" when neutral).
 */
object Speaker {
    const val MALE = "male"
    const val FEMALE = "female"

    /** Male or female. An older "neutral" choice reads as male. */
    fun get(ctx: Context): String {
        val v = Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).getString("speaker", MALE)
        return if (v == FEMALE) FEMALE else MALE
    }

    fun set(ctx: Context, v: String) =
        Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("speaker", v).apply()

    private const val COMMON = """- "I", "me" and "my" are the speaker; "you" and "your" are the listener. Never swap them, and never add "tum" or "aap" that is not in the English.
- Translate every sentence, including questions. Never answer one: "How are you?" is asked to the listener ("Tum kaisay ho?" casual, "Aap kaisay hain?" formal); only "I am fine" is about the speaker.
- Statements stay statements and questions stay questions. "We" is "hum".
- For the listener use plural verb forms: tum kar rahay ho, aap kar rahay hain."""

    /** Rules block appended to the Roman Urdu system prompt. */
    fun rules(ctx: Context): String = "\n" + when (get(ctx)) {
        FEMALE -> """- The speaker is female. When the English is about the speaker, write "main" with feminine first-person forms and "hoon": main thak gayi hoon, main kaam kar rahi hoon, main aa sakti hoon, main bhool gayi thi, main baad mein call karti hoon."""
        else -> """- The speaker is male. When the English is about the speaker, write "main" with masculine first-person forms and "hoon": main thak gaya hoon, main kaam kar raha hoon, main aa sakta hoon, main bhool gaya tha, main baad mein call karta hoon."""
    } + "\n" + COMMON

    /** Old rule lines that made the speaker sound like "you"; removed from saved custom rules. */
    fun stripOldRules(rules: String): String = rules.lines().filterNot {
        val t = it.trimStart()
        t.startsWith("- Gender-neutral:") || t.startsWith("- When the speaker talks about themself")
    }.joinToString("\n")

    private val toFemale = mapOf(
        "raha" to "rahi", "gaya" to "gayi", "karta" to "karti", "sakta" to "sakti", "chahta" to "chahti",
        "tha" to "thi", "wala" to "wali", "deta" to "deti", "leta" to "leti", "jata" to "jati", "aata" to "aati",
    )
    private val word = Regex("[A-Za-z]+")
    private val clause = Regex("[^,.?!;]+[,.?!;]*")

    /** Rewrites the male first-person forms of the default bank for the chosen speaker. */
    fun adapt(ctx: Context, roman: String): String {
        val map = if (get(ctx) == FEMALE) toFemale else return roman
        return clause.replace(roman) { c ->
            val text = c.value
            if (word.findAll(text).none { it.value.equals("main", true) || it.value.equals("hoon", true) }) text
            else word.replace(text) { w ->
                val repl = map[w.value.lowercase()] ?: return@replace w.value
                if (w.value[0].isUpperCase()) repl.replaceFirstChar { it.uppercase() } else repl
            }
        }
    }
}
