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
}
