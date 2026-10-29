package com.paul.urdukb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneBankTest {
    private val WORD = Regex("[\\p{L}']+")

    @Test fun everyExtraToneHasABankAndRules() {
        for (style in Style.entries.filter { it.isExtraTone }) {
            val bank = ToneBank.forStyle(style)
            assertNotNull("bank for $style", bank)
            val pairs = Bank.parse(bank!!)
            assertTrue("$style has ${pairs.size} pairs", pairs.size >= 14)
            val groups = bank.lines().count { it.startsWith("#") }
            assertTrue("$style has $groups situations", groups >= 5)
            assertNotNull("rules for $style", ToneRules.forStyle(style))
        }
        assertEquals(null, ToneBank.forStyle(Style.CASUAL))
    }

    @Test fun bankOutputUsesTheUrduLetterSpellings() {
        val mistakes = SpellingRules.PAIRS.associate { it.first.lowercase() to it.second }
        val found = ArrayList<String>()
        for (style in Style.entries.filter { it.isExtraTone }) {
            for ((en, ur) in Bank.parse(ToneBank.forStyle(style)!!)) {
                for (w in WORD.findAll(ur.lowercase())) mistakes[w.value]?.let { found += "$style: ${w.value} should be $it in \"$en\"" }
            }
        }
        assertTrue("Hindi-style spellings in the tone banks: $found", found.isEmpty())
    }

    @Test fun banksUseTheAySpelling() {
        // "-te hain" style endings should be "-tay hain" in the examples, as in the other banks
        val bad = ArrayList<String>()
        val endings = Regex("\\b(kar|ho|ja|mil|rakh|sun|bol|dekh|chah|sak)te\\b")
        for (style in Style.entries.filter { it.isExtraTone }) {
            for ((_, ur) in Bank.parse(ToneBank.forStyle(style)!!)) endings.find(ur.lowercase())?.let { bad += "$style: ${it.value} in $ur" }
        }
        assertTrue("use -tay: $bad", bad.isEmpty())
    }

    @Test fun banksKeepTheirSpellingConsistent() {
        // "mere/tumhare/humare" must be "meray/tumharay/humaray", "achi" must be "achhi", and "aram" must be "aaram"
        val bad = Regex("\\b(mere|tumhare|humare|aapke|achi|achay|aram|araam|mazaaqiya)\\b", RegexOption.IGNORE_CASE)
        val found = ArrayList<String>()
        for (style in Style.entries.filter { it.isExtraTone }) {
            for ((_, ur) in Bank.parse(ToneBank.forStyle(style)!!)) bad.find(ur)?.let { found += "$style: ${it.value} in $ur" }
        }
        assertTrue("inconsistent spellings: $found", found.isEmpty())
    }

    @Test fun savedBanksGetTheCorrectedLines() {
        // every line the review corrected is a replacement for banks saved on a phone before
        for ((old, new) in DefaultBank.REPLACED) {
            assertTrue("replacement keeps the English side: $old", old.substringBefore("|") == new.substringBefore("|"))
        }
        assertTrue(DefaultBank.REPLACED.size >= 11 + 10)
    }

    @Test fun existingBanksReport() {
        // not an assertion: shows how many Hindi-style spellings the older banks carry, so they can be cleaned up
        val mistakes = SpellingRules.PAIRS.associate { it.first.lowercase() to it.second }
        for ((name, text) in listOf("casual" to DefaultBank.CASUAL, "formal" to DefaultBank.FORMAL)) {
            val hits = Bank.parse(text).flatMap { (_, ur) -> WORD.findAll(ur.lowercase()).mapNotNull { m -> mistakes[m.value]?.let { "${m.value}->$it" } }.toList() }
            println("BANKSPELLING $name: ${hits.size} ${hits.take(12)}")
        }
    }
}
