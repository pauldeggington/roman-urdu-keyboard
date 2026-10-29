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

    @Test fun reconcilingBuildsOneLabelledMessage() {
        val m = VoicePrompts.reconcileInput(listOf("normal speed" to "a b", "slowed down" to "a c", "faster" to "", "Roman Urdu" to "a b"))
        assertEquals("Transcript 1 (normal speed): a b\nTranscript 2 (slowed down): a c\nTranscript 3 (Roman Urdu): a b", m)
        assertTrue(VoicePrompts.RECONCILE_RULES.contains("most likely correct transcript"))
    }

    @Test fun romanPromptAsksForRomanUrdu() {
        assertTrue(VoicePrompts.TRANSCRIBE_ROMAN.contains("Roman Urdu"))
        assertFalse(VoicePrompts.TRANSCRIBE_ROMAN.contains("Urdu script"))
    }

    @Test fun sameTextIgnoresPunctuationAndSpacing() {
        assertTrue(VoicePrompts.sameText("Parcel  reception!", "parcel reception"))
        assertFalse(VoicePrompts.sameText("parcel reception", "parcel reception ok"))
        assertTrue(VoicePrompts.sameText("", " "))
    }

    @Test fun translationExamplesMixUrduAndEnglish() {
        assertTrue(VoicePrompts.TRANSLATE_EXAMPLES.size >= 3)
        for ((urdu, english) in VoicePrompts.TRANSLATE_EXAMPLES) {
            assertTrue("urdu side has Urdu letters", urdu.any { it.code in 0x0600..0x06FF })
            assertTrue("urdu side keeps an English word", Regex("[A-Za-z]{3,}").containsMatchIn(urdu))
            assertFalse("english side is English", VoicePrompts.isUrduScript(english))
        }
    }
}
