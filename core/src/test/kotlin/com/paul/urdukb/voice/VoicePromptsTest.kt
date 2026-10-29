package com.paul.urdukb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptsTest {

    @Test fun readsTheTwoLineReply() {
        val h = VoicePrompts.parseHeard("Urdu: میں ٹھیک ہوں\nEnglish: I am fine")!!
        assertEquals("میں ٹھیک ہوں", h.urdu)
        assertEquals("I am fine", h.english)
    }

    @Test fun readsAMessyReply() {
        val h = VoicePrompts.parseHeard("  urdu : \"آپ کیسے ہیں\"  \n\nENGLISH:   How are you?\n")!!
        assertEquals("How are you?", h.english)
        assertTrue(VoicePrompts.isUrduScript(h.urdu))
    }

    @Test fun readsARepliesWithoutMarkers() {
        assertEquals("Hello there", VoicePrompts.parseHeard("Hello there")!!.english)
        val onlyUrdu = VoicePrompts.parseHeard("آپ کیسے ہیں")!!
        assertEquals("", onlyUrdu.english)
        assertTrue(onlyUrdu.urdu.isNotEmpty())
    }

    @Test fun emptyRepliesAreRejected() {
        assertNull(VoicePrompts.parseHeard(""))
        assertNull(VoicePrompts.parseHeard("   \n"))
        assertNotNull(VoicePrompts.parseHeard("ok"))
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

    @Test fun promptsAskForTheFormatTheParserReads() {
        assertTrue(VoicePrompts.URDU_TO_ENGLISH.contains("Urdu:") && VoicePrompts.URDU_TO_ENGLISH.contains("English:"))
        // the format in the prompt, filled in, is read back correctly
        val h = VoicePrompts.parseHeard("Urdu: ا\nEnglish: a")!!
        assertEquals("a", h.english)
    }
}
