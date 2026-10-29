package com.paul.urdukb

import java.util.Calendar
import kotlin.random.Random

/** The app's voice. Roman Urdu, gender-neutral (kartay, rahay, kaisay), a little playful. */
object Words {

    fun greeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Subah bakhair! Chai pi li?"
            in 11..14 -> "Salam! Dopahar mein kya chal raha hai?"
            in 15..18 -> "Salam! Chai ka waqt ho gaya."
            in 19..22 -> "Shaam bakhair! Aaj ka din kaisa raha?"
            else -> "Itni raat ko? Chalo, translate kartay hain."
        }
    }

    private val thinking = listOf(
        "Soch rahay hain…",
        "Zara sa sabar…",
        "Lafz dhoond rahay hain…",
        "Dimaag ghoom raha hai…",
        "Chai ka ghoont lo, abhi hua…",
        "Alfaaz tol rahay hain…",
    )

    private val done = listOf(
        "Lo, ho gaya.",
        "Yeh lijiye.",
        "Tayyar hai!",
        "Pesh e khidmat.",
        "Ho gaya kaam.",
    )

    private val copied = listOf(
        "Copy ho gaya!",
        "Ab jahan marzi paste karein.",
        "Clipboard mein pohanch gaya.",
    )

    private val colour = listOf(
        "Naya rang, naya mood.",
        "Rang badal gaya!",
        "Bahut khoob, achha lag raha hai.",
    )

    private var lastThinking = -1

    /** A different "working on it" line each time. */
    fun thinking(): String {
        var i = Random.nextInt(thinking.size)
        if (i == lastThinking) i = (i + 1) % thinking.size
        lastThinking = i
        return thinking[i]
    }

    fun done(): String = done.random()
    fun copied(): String = copied.random()
    fun colour(): String = colour.random()
}
