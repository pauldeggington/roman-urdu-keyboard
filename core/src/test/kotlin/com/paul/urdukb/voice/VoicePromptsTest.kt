package com.paul.urdukb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptsTest {

    @Test fun namesAreAddedToTheListeningPrompt() {
        val p = VoicePrompts.withNames(VoicePrompts.TRANSCRIBE_URDU, listOf("Paul", " AJ Tower ", "", "Paul"))
        assertTrue(p.startsWith(VoicePrompts.TRANSCRIBE_URDU))
        assertTrue(p.endsWith("Names and places that may be said: Paul, AJ Tower."))
        assertEquals(VoicePrompts.TRANSCRIBE_URDU, VoicePrompts.withNames(VoicePrompts.TRANSCRIBE_URDU, emptyList()))
        assertEquals(VoicePrompts.TRANSCRIBE_URDU, VoicePrompts.withNames(VoicePrompts.TRANSCRIBE_URDU, listOf("  ", "")))
    }

    @Test fun theNameListIsReadFromWhatThePersonTyped() {
        assertEquals(listOf("Paul", "AJ Tower", "Gulberg"), VoicePrompts.parseNames("Paul\nAJ Tower, Gulberg\n\n"))
        assertTrue(VoicePrompts.parseNames("  \n ").isEmpty())
    }

    @Test fun neverMoreThanThirtyNames() {
        val many = (1..50).map { "Name$it" }
        val p = VoicePrompts.withNames("x", many)
        assertTrue(p.contains("Name30") && !p.contains("Name31"))
    }

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
