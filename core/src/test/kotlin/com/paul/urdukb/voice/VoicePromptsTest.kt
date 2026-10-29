package com.paul.urdukb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptsTest {

    @Test fun transcriptsAreCleaned() {
        assertEquals("See you tomorrow.", VoicePrompts.cleanTranscript("\"See you tomorrow.\"<end_of_turn>"))
        assertEquals("See you tomorrow.", VoicePrompts.cleanTranscript("Transcript: See you tomorrow."))
        assertEquals("a b c", VoicePrompts.join(listOf(" a ", "", "b", " c")))
    }

    @Test fun urduScriptDetection() {
        assertTrue(VoicePrompts.isUrduScript("میں ٹھیک ہوں"))
        assertTrue(VoicePrompts.isUrduScript("میں ok ہوں"))
        assertFalse(VoicePrompts.isUrduScript("Main theek hoon"))
        assertFalse(VoicePrompts.isUrduScript("12345 !!"))
        assertFalse(VoicePrompts.isUrduScript(""))
    }

    @Test fun theTwoStepsAreSeparate() {
        // hearing only transcribes; translating is a different call with its own rules
        assertFalse(VoicePrompts.TRANSCRIBE_URDU.contains("English:"))
        assertTrue(VoicePrompts.URDU_TEXT_TO_ENGLISH_RULES.contains("AJ Tower"))
    }

    @Test fun reconcilingBuildsOneMessageWithBothTranscripts() {
        val m = VoicePrompts.reconcileInput("a b", "a c")
        assertTrue(m.contains("normal speed): a b") && m.contains("slowed down): a c"))
        assertTrue(VoicePrompts.RECONCILE_RULES.contains("most likely correct transcript"))
    }

    @Test fun sameTextIgnoresPunctuationAndSpacing() {
        assertTrue(VoicePrompts.sameText("Parcel  reception!", "parcel reception"))
        assertFalse(VoicePrompts.sameText("parcel reception", "parcel reception ok"))
        assertTrue(VoicePrompts.sameText("", " "))
    }

    @Test fun translationExamplesMixUrduAndEnglish() {
        assertTrue(VoicePrompts.TRANSLATE_EXAMPLES.size >= 3)
        for ((urdu, english) in VoicePrompts.TRANSLATE_EXAMPLES) {
            assertTrue("urdu side has Urdu letters", VoicePrompts.isUrduScript(urdu))
            assertTrue("urdu side keeps an English word", Regex("[A-Za-z]{3,}").containsMatchIn(urdu))
            assertFalse("english side is English", VoicePrompts.isUrduScript(english))
        }
    }
}
