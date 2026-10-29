package com.paul.urdukb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteSimilarityTest {

    @Test fun twoOfThreeWin() {
        assertEquals("a b", Vote.majority(listOf("a b", "a c", "A  b!")))
    }

    @Test fun noAgreementMeansNoWinner() {
        assertNull(Vote.majority(listOf("a b", "a c", "d")))
        assertNull(Vote.majority(listOf("only one")))
        assertNull(Vote.majority(emptyList()))
    }

    @Test fun emptyTextsDoNotVote() {
        assertNull(Vote.majority(listOf("", " ", "!")))
        assertEquals("x", Vote.majority(listOf("", "x", "x")))
    }

    private val heard = "\u06CC\u06C1 \u067E\u0627\u0644 \u06A9\u06D2 \u0646\u0627\u0645 \u067E\u0631 \u067E\u0627\u0631\u0633\u0644 \u062A\u06BE\u0627 \u0631\u06CC\u0633\u067E\u0634\u0646 \u067E\u0631 \u062F\u06D2 \u062F\u06CC\u0627 \u06C1\u06D2"

    @Test fun sameTextScoresOne() {
        assertEquals(1.0, Similarity.dice(heard, heard), 0.0001)
    }

    @Test fun variantSpellingsOfALetterCountTheSame() {
        // the two kinds of ye, and the two kinds of he, are one letter for the comparison
        val variant = heard.replace('\u06CC', '\u0649').replace('\u06C1', '\u06BE')
        assertTrue(Similarity.dice(heard, variant) > 0.95)
    }

    @Test fun aDifferentSentenceScoresLow() {
        val other = "\u0622\u062C \u0645\u0648\u0633\u0645 \u0628\u06C1\u062A \u0627\u0686\u06BE\u0627 \u06C1\u06D2 \u0627\u0648\u0631 \u06C1\u0645 \u0628\u0627\u06C1\u0631 \u062C\u0627\u0626\u06CC\u06BA \u06AF\u06D2"
        assertTrue("score ${Similarity.dice(heard, other)}", Similarity.dice(heard, other) < 0.25)
    }

    @Test fun aSlightlyReorderedSentenceStaysClose() {
        val reordered = "\u067E\u0627\u0631\u0633\u0644 \u067E\u0627\u0644 \u06A9\u06D2 \u0646\u0627\u0645 \u067E\u0631 \u062A\u06BE\u0627 \u0631\u06CC\u0633\u067E\u0634\u0646 \u067E\u0631 \u062F\u06D2 \u062F\u06CC\u0627 \u06C1\u06D2"
        assertTrue("score ${Similarity.dice(heard, reordered)}", Similarity.dice(heard, reordered) > 0.5)
    }

    @Test fun latinWordsAreIgnored() {
        val withLatin = heard + " AJ Tower reception parcel"
        assertEquals(1.0, Similarity.dice(heard, withLatin), 0.0001)
    }

    @Test fun tooLittleUrduCannotFail() {
        assertEquals(1.0, Similarity.dice("hello there", heard), 0.0)
        assertEquals(1.0, Similarity.dice("\u067E\u0627\u0644", "\u0622\u062C"), 0.0)
    }
}
