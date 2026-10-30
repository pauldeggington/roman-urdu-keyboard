package com.paul.urdukb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningRulesTest {

    @Test fun bothDirectionsSayTranslateTheMeaning() {
        assertTrue(MeaningRules.TO_ENGLISH.contains("verb at the end") && MeaningRules.TO_ENGLISH.contains("what is meant"))
        assertTrue(MeaningRules.TO_URDU.contains("verb goes at the end") && MeaningRules.TO_URDU.contains("intended meaning"))
        assertTrue(MeaningRules.TO_URDU_SCRIPT.contains("verb at the end"))
        assertTrue(com.paul.urdukb.voice.VoicePrompts.URDU_SCRIPT_RULES.contains("verb at the end"))
    }

    @Test fun orderExamplesAreReadAsPairsUnderOneHeading() {
        val grouped = Bank.parseGrouped(DefaultBank.ADDED_ORDER.getValue("english"))
        assertTrue(grouped.size >= 6)
        assertTrue(grouped.all { it.group == "meaning, not word order" })
        // the English side is natural English, not a copy of the Urdu order
        val first = grouped.first { it.input.startsWith("yeh kitab") }
        assertEquals("I bought this book from the market yesterday.", first.output)
    }

    @Test fun theDefaultEnglishBankCarriesThem() {
        val pairs = Bank.parse(DefaultBank.TO_ENGLISH)
        assertTrue(pairs.any { it.first == "usko sab kuch pata hai" })
        assertTrue(pairs.any { it.first.startsWith("yeh paul ke naam par parcel") })
    }

    @Test fun theOrderExamplesFollowTheSpellingRules() {
        // Roman Urdu spelled by the Urdu letters, same as every other bank
        val mistakes = SpellingRules.PAIRS.associate { it.first.lowercase() to it.second }
        val word = Regex("[\\p{L}']+")
        val found = Bank.parse(DefaultBank.ADDED_ORDER.getValue("english")).flatMap { (ur, _) -> word.findAll(ur.lowercase()).mapNotNull { m -> mistakes[m.value]?.let { "${m.value}->$it" } }.toList() }
        assertTrue("Hindi-style spellings: $found", found.isEmpty())
    }
}
