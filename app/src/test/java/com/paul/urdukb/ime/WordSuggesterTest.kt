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
        assertEquals("the", suggester.suggest("teh").first())
        assertEquals("receive", suggester.suggest("recieve").first())
        assertEquals("definitely", suggester.suggest("definately").first())
        assertEquals("with", suggester.suggest("wiht").first())
        assertEquals("because", suggester.suggest("becuase").first())
        assertEquals("their", suggester.suggest("thier").first())
        assertEquals("tomorrow", suggester.suggest("tommorow").first())
    }

    @Test
    fun anApostropheLessContractionIsOffered() {
        assertTrue("dont: " + suggester.suggest("dont"), "don't" in suggester.suggest("dont"))
        assertTrue("wasnt: " + suggester.suggest("wasnt"), "wasn't" in suggester.suggest("wasnt"))
        assertTrue("im: " + suggester.suggest("im"), "i'm" in suggester.suggest("im"))
    }

    @Test
    fun realWordsAreNotChanged() {
        assertEquals("form", suggester.suggest("form").first())
        assertEquals("from", suggester.suggest("from").first())
        assertEquals("was", suggester.suggest("was").first())
    }

    @Test
    fun aRealWordStaysFirst() {
        assertEquals("the", suggester.suggest("the").first())
        assertEquals("hello", suggester.suggest("hello").first())
        assertEquals("computer", suggester.suggest("computer").first())
    }

    @Test
    fun yourOwnWordsAreSuggested() {
        val personal = mapOf("paulu" to 6, "zeeshan" to 12)
        assertTrue("zeeshan in " + suggester.suggest("zee", personal), "zeeshan" in suggester.suggest("zee", personal))
        assertTrue("paulu in " + suggester.suggest("pau", personal), "paulu" in suggester.suggest("pau", personal))
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

    // ---------------------------------------------------------------- automatic correction
    @Test
    fun correctsClearTypos() {
        assertEquals("the", suggester.autocorrect("teh"))
        assertEquals("receive", suggester.autocorrect("recieve"))
        assertEquals("definitely", suggester.autocorrect("definately"))
        assertEquals("because", suggester.autocorrect("becuase"))
        assertEquals("their", suggester.autocorrect("thier"))
        assertEquals("tomorrow", suggester.autocorrect("tommorow"))
    }

    @Test
    fun correctsMissingApostrophes() {
        assertEquals("don't", suggester.autocorrect("dont"))
        assertEquals("i'm", suggester.autocorrect("im"))
        assertEquals("can't", suggester.autocorrect("cant"))
    }

    @Test
    fun leavesRealWordsAndOwnWordsAlone() {
        for (w in listOf("form", "from", "was", "hello", "computer", "its", "were", "well", "ill", "good", "morning", "friend", "tomorrow"))
            assertEquals("changed $w", null, suggester.autocorrect(w))
        assertEquals(null, suggester.autocorrect("zeeshan", mapOf("zeeshan" to 5)))
        assertEquals(null, suggester.autocorrect("teh", mapOf("teh" to 3)))   // the person types this on purpose
    }

    @Test
    fun doesNotGuessWhenUnsure() {
        assertEquals(null, suggester.autocorrect("xqzt"))
        assertEquals(null, suggester.autocorrect("ab"))
        assertEquals(null, suggester.autocorrect("hello!"))
    }

    @Test
    fun fixesDroppedLettersAndShorthand() {
        assertEquals("hello", suggester.autocorrect("helo"))
        assertEquals("which", suggester.autocorrect("wich"))
        assertEquals("sorry", suggester.autocorrect("sory"))
        assertEquals("please", suggester.autocorrect("pleas"))
        assertEquals("remember", suggester.autocorrect("rember"))
        assertEquals("thanks", suggester.autocorrect("thanx"))
        assertEquals("a lot", suggester.autocorrect("alot"))
    }

    @Test
    fun doesNotShortenWords() {
        assertEquals("how's", suggester.autocorrect("hows"))
    }
}
