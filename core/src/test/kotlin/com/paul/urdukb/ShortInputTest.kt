package com.paul.urdukb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortInputTest {

    @Test fun recognisesBareWordsAndShortPhrases() {
        assertTrue(ShortInput.isShort("friend"))
        assertTrue(ShortInput.isShort("Good morning"))
        assertTrue(ShortInput.isShort("  best friend "))
        assertFalse(ShortInput.isShort("I am tired"))
        assertFalse(ShortInput.isShort("How are you"))
        assertFalse(ShortInput.isShort("Friend?"))
        assertFalse(ShortInput.isShort("Thanks."))
        assertFalse(ShortInput.isShort("see you tomorrow morning ok"))
        assertFalse(ShortInput.isShort("what"))
        assertFalse(ShortInput.isShort(""))
    }

    @Test fun catchesAQuestionOrASentenceInsteadOfTheWord() {
        assertTrue(ShortInput.wrong("friend", "Aap ka dost kaun hai?"))
        assertTrue(ShortInput.wrong("friend", "Kya aap mere dost hain"))
        assertTrue(ShortInput.wrong("friend", "Aap dost hain"))
        assertTrue(ShortInput.wrong("water", "Tum pani peena chahtay ho aaj raat ko"))
        assertTrue(ShortInput.wrong("tea", "Chai?"))
    }

    @Test fun acceptsTheWordItself() {
        assertFalse(ShortInput.wrong("friend", "Dost"))
        assertFalse(ShortInput.wrong("Good morning", "Subah bakhair"))
        assertFalse(ShortInput.wrong("best friend", "Sab se achha dost"))
        assertFalse(ShortInput.wrong("family", "Ghar walay"))
        // full sentences are not this guard's business
        assertFalse(ShortInput.wrong("How are you?", "Aap kaisay hain?"))
        assertFalse(ShortInput.wrong("Are you coming tonight", "Kya aap aaj raat aa rahay hain?"))
    }

    @Test fun everyBankHasTheWordExamples() {
        for (key in listOf("casual", "formal")) {
            val entries = Bank.parseGrouped(DefaultBank.ADDED_WORDS.getValue(key)).filter { it.group == ShortInput.GROUP }
            assertTrue("$key has ${entries.size} word examples", entries.size >= 30)
            // none of them is a question or a sentence
            val bad = entries.filter { ShortInput.isShort(it.input) && ShortInput.wrong(it.input, it.output) }
            assertEquals("word examples that look wrong to the guard: ${bad.map { it.input }}", 0, bad.size)
        }
        val casual = Bank.parseGrouped(DefaultBank.CASUAL).count { it.group == ShortInput.GROUP }
        val formal = Bank.parseGrouped(DefaultBank.FORMAL).count { it.group == ShortInput.GROUP }
        assertTrue("default banks carry them: $casual / $formal", casual >= 30 && formal >= 30)
        // the formal bank says "aap", the casual one "tum"
        val formalYour = Bank.parseGrouped(DefaultBank.FORMAL).first { it.input == "Your friend" }.output
        assertEquals("Aap ka dost", formalYour)
    }
}
