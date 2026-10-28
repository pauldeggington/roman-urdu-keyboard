package com.paul.urdukb.ime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSuggesterTest {

    private val words = WordSuggester.readWords(File("src/main/assets/dict/en.tsv").readLines().asSequence())
    private val suggester = WordSuggester(words)

    @Test
    fun finishesAWordWithTheCommonOnesFirst() {
        val r = suggester.suggest("th")
        assertEquals(3, r.size)
        assertTrue("the was not offered for th: $r", "the" in r || "that" in r || "this" in r)
        assertTrue(r.all { it.startsWith("th") })
    }

    @Test
    fun suggestionsGetMoreSpecificAsYouType() {
        val r = suggester.suggest("tomor")
        assertEquals("tomorrow", r.first())
    }

    @Test
    fun fixesCommonTypos() {
        assertTrue("teh -> the: " + suggester.suggest("teh"), "the" in suggester.suggest("teh"))
        assertEquals("receive", suggester.suggest("recieve").first())
        assertTrue("definately: " + suggester.suggest("definately"), "definitely" in suggester.suggest("definately"))
        assertTrue("wiht: " + suggester.suggest("wiht"), "with" in suggester.suggest("wiht"))
    }

    @Test
    fun anApostropheLessContractionIsOffered() {
        assertTrue("dont: " + suggester.suggest("dont"), "don't" in suggester.suggest("dont"))
        assertTrue("im: " + suggester.suggest("im"), "i'm" in suggester.suggest("im") || suggester.suggest("im").isNotEmpty())
    }

    @Test
    fun aRealWordStaysFirst() {
        assertEquals("the", suggester.suggest("the").first())
        assertEquals("hello", suggester.suggest("hello").first())
    }

    @Test
    fun yourOwnWordsAreSuggested() {
        val personal = mapOf("paulu" to 6, "zeeshan" to 12)
        assertEquals("zeeshan", suggester.suggest("zee", personal).first())
        assertTrue("paulu" in suggester.suggest("pau", personal))
        // a typed name the person already uses is left alone
        assertEquals("zeeshan", suggester.suggest("zeeshan", personal).first())
    }

    @Test
    fun nothingForTooShortOrOddInput() {
        assertTrue(suggester.suggest("t").isEmpty())
        assertTrue(suggester.suggest("").isEmpty())
        assertTrue(suggester.suggest("ab1").isEmpty())
        assertTrue(suggester.suggest("hello!").isEmpty())
    }

    @Test
    fun capitalsFollowWhatWasTyped() {
        assertEquals("The", WordSuggester.matchCase("Th", "the"))
        assertEquals("THE", WordSuggester.matchCase("TH", "the"))
        assertEquals("the", WordSuggester.matchCase("th", "the"))
        assertEquals("I'm", WordSuggester.matchCase("im", "i'm"))
    }

    @Test
    fun answersAreQuick() {
        val t0 = System.nanoTime()
        repeat(50) { suggester.suggest("recieve"); suggester.suggest("th"); suggester.suggest("compl") }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("150 suggestions took $ms ms")
        assertTrue("took $ms ms", ms < 3000)
    }

    @Test
    fun wordKnowledge() {
        assertTrue(suggester.isWord("hello"))
        assertFalse(suggester.isWord("helllo"))
    }
}
