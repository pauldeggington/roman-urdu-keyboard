package com.paul.urdukb

import com.paul.urdukb.ime.GlideCase
import com.paul.urdukb.ime.KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of the translator that are plain logic: spelling glossary, who-is-speaking tags, example picking. */
class LogicTest {

    // ---------------------------------------------------------------- glossary
    private val glossary = listOf("bada" to "barha", "kaise" to "kaisay", "kya hai" to "kia hai")

    @Test
    fun glossaryKeepsCapitals() {
        assertEquals("Barha hai", Glossary.apply(glossary, "Bada hai"))
        assertEquals("BARHA", Glossary.apply(glossary, "BADA"))
    }

    @Test
    fun glossaryOnlyChangesWholeWords() {
        assertEquals("badaam bada’s", Glossary.apply(listOf("bada" to "barha"), "badaam bada’s").replace("barha", "bada"))
        assertEquals("badaam", Glossary.apply(glossary, "badaam"))
        assertEquals("don't", Glossary.apply(listOf("don" to "x"), "don't"))
    }

    @Test
    fun glossaryAlsoFixesOtherSpellings() {
        val cluster = { w: String -> if (w == "kaise") listOf("kasay", "kesay") else emptyList() }
        assertEquals("kaisay ho", Glossary.apply(glossary, "kesay ho", cluster))
    }

    @Test
    fun glossaryPhrases() {
        assertEquals("Kia hai yeh", Glossary.apply(glossary, "Kya hai yeh"))
    }

    @Test
    fun glossaryLeavesEmptyAndUnknownTextAlone() {
        assertEquals("", Glossary.apply(glossary, ""))
        assertEquals("hello there", Glossary.apply(glossary, "hello there"))
        assertEquals("anything", Glossary.apply(emptyList(), "anything"))
    }

    // ---------------------------------------------------------------- I versus you
    @Test
    fun tagsSayWhoASentenceIsAbout() {
        assertEquals("(you) How are you?", Person.tag("How are you?", Style.CASUAL))
        assertEquals("(I) I am well.", Person.tag("I am well.", Style.CASUAL))
        assertEquals("(you) Where are you? (I) I've been waiting.", Person.tag("Where are you? I've been waiting.", Style.CASUAL))
        assertEquals("Thanks, that helped.", Person.tag("Thanks, that helped.", Style.CASUAL))
        assertEquals("(main) Main theek hoon.", Person.tag("Main theek hoon.", Style.TO_ENGLISH))
        assertEquals("(tum/aap) Tum kahan ho?", Person.tag("Tum kahan ho?", Style.TO_ENGLISH))
    }

    @Test
    fun overallIsOnlySetWhenOnePersonIsInvolved() {
        assertEquals(Person.Who.LISTENER, Person.overall("How are you?", Style.CASUAL))
        assertEquals(Person.Who.SPEAKER, Person.overall("I am well.", Style.CASUAL))
        assertNull(Person.overall("I love you.", Style.CASUAL))
        assertNull(Person.overall("I'm fine, how about you?", Style.CASUAL))
        assertNull(Person.overall("Please call me back.", Style.CASUAL))
    }

    @Test
    fun answersAboutTheWrongPersonAreCaught() {
        assertFalse(Person.consistent("Main theek hoon.", Person.Who.LISTENER, Style.CASUAL))
        assertTrue(Person.consistent("Tum kaisay ho?", Person.Who.LISTENER, Style.CASUAL))
        assertFalse(Person.consistent("Tum theek ho.", Person.Who.SPEAKER, Style.CASUAL))
        assertTrue(Person.consistent("Main theek hoon.", Person.Who.SPEAKER, Style.CASUAL))
        assertTrue(Person.consistent("Khana khaya?", Person.Who.LISTENER, Style.CASUAL))   // no pronoun at all is fine
        assertFalse(Person.consistent("I'm fine.", Person.Who.LISTENER, Style.TO_ENGLISH))
        assertTrue(Person.consistent("You are fine.", Person.Who.LISTENER, Style.TO_ENGLISH))
    }

    @Test
    fun tagsAreRemovedIfTheModelWritesThemBack() {
        assertEquals("Tum kaisay ho?", Person.stripTags("(you) Tum kaisay ho?"))
        assertEquals("I am fine", Person.stripTags("(main) I am fine"))
    }

    // ---------------------------------------------------------------- example picking
    @Test
    fun theClosestExampleComesLast() {
        val bank = Bank.parse("How are you? | Tum kaisay ho?\nI'm hungry. | Bhook lagi hai.\nSee you tomorrow. | Kal milte hain.\nWhat time is it? | Kya time hua hai?\nThanks a lot. | Bohat shukriya.\nWhere is the station? | Station kahan hai?\nOpen the door. | Darwaza kholo.")
        val picked = Bank.pick(bank, "How are you doing?", k = 3)
        assertEquals(3, picked.size)
        assertEquals("How are you?", picked.last().first)
    }

    @Test
    fun parsingTwiceGivesTheSameListSoWorkCanBeReused() {
        val text = "a | b\nc | d"
        assertSame(Bank.parse(text), Bank.parse(text))
        assertEquals(listOf("a" to "b", "c" to "d"), Bank.parse(text))
    }

    @Test
    fun badLinesInABankAreSkipped() {
        assertEquals(listOf("a" to "b"), Bank.parse("no bar here\n | missing left\nmissing right |\na | b"))
    }

    // ---------------------------------------------------------------- glided words
    @Test
    fun glidedWordsGetTheRightCapitals() {
        assertEquals("hello", GlideCase.apply("hello", KeyboardView.SHIFT_OFF))
        assertEquals("Hello", GlideCase.apply("hello", KeyboardView.SHIFT_ON))
        assertEquals("HELLO", GlideCase.apply("hello", KeyboardView.SHIFT_CAPS))
        assertEquals("I", GlideCase.apply("i", KeyboardView.SHIFT_OFF))
        assertEquals("I'm", GlideCase.apply("i'm", KeyboardView.SHIFT_OFF))
    }
}
