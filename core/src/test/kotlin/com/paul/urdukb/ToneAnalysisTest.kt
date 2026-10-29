package com.paul.urdukb

import com.paul.urdukb.ToneAnalysis.Listener
import com.paul.urdukb.ToneAnalysis.Situation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneAnalysisTest {

    @Test fun parsesAWellFormedReply() {
        val a = ToneAnalysis.parse("tone=respectful; listener=elder; situation=care")!!
        assertEquals(Style.RESPECTFUL, a.tone)
        assertEquals(Listener.ELDER, a.listener)
        assertEquals(Situation.CARE, a.situation)
    }

    @Test fun parsingIsForgiving() {
        val a = ToneAnalysis.parse("  Tone: Poetic ; Listener: partner ; Situation: feelings.\n")!!
        assertEquals(Style.POETIC, a.tone)
        assertEquals(Listener.PARTNER, a.listener)
        assertEquals(Situation.FEELINGS, a.situation)
        // a missing or unknown part falls back instead of failing
        val b = ToneAnalysis.parse("tone=funny")!!
        assertEquals(Listener.UNKNOWN, b.listener)
        assertEquals(Situation.OTHER, b.situation)
    }

    @Test fun unusableRepliesAreIgnored() {
        assertNull(ToneAnalysis.parse(""))
        assertNull(ToneAnalysis.parse("Main theek hoon"))
        assertNull(ToneAnalysis.parse("tone=english; listener=unknown"))
        assertNull(ToneAnalysis.parse("tone=sarcastic; listener=unknown"))
    }

    @Test fun theModelsToneWinsOverTheKeywordGuess() {
        val a = ToneAnalysis.Analysis(Style.POETIC, Listener.PARTNER, Situation.FEELINGS)
        assertEquals(Style.POETIC, ToneAnalysis.decide(Style.CASUAL, a, Style.CASUAL))
        assertEquals(Style.CASUAL, ToneAnalysis.decide(Style.CASUAL, null, Style.CASUAL))
    }

    @Test fun anElderAlwaysGetsTheRespectfulTone() {
        val a = ToneAnalysis.Analysis(Style.CASUAL, Listener.ELDER, Situation.GREETING)
        assertEquals(Style.RESPECTFUL, ToneAnalysis.decide(Style.CASUAL, a, Style.CASUAL))
        assertEquals(Style.RESPECTFUL, ToneAnalysis.decide(Style.FORMAL, a, Style.FORMAL))
    }

    @Test fun colleaguesAndStrangersNeverGetSlang() {
        for (listener in listOf(Listener.COLLEAGUE, Listener.STRANGER)) {
            val funny = ToneAnalysis.Analysis(Style.FUNNY, listener, Situation.TEASING)
            assertEquals(Style.FORMAL, ToneAnalysis.decide(Style.CASUAL, funny, Style.CASUAL))
            val casual = ToneAnalysis.Analysis(Style.CASUAL, listener, Situation.REQUEST)
            assertEquals(Style.FORMAL, ToneAnalysis.decide(Style.CASUAL, casual, Style.CASUAL))
        }
    }

    @Test fun peopleWhoChoseFormalStayFormalExceptForEldersAndPoetry() {
        val funny = ToneAnalysis.Analysis(Style.FUNNY, Listener.FRIEND, Situation.TEASING)
        assertEquals(Style.FORMAL, ToneAnalysis.decide(Style.FORMAL, funny, Style.FORMAL))
        val casual = ToneAnalysis.Analysis(Style.CASUAL, Listener.FRIEND, Situation.PLANS)
        assertEquals(Style.FORMAL, ToneAnalysis.decide(Style.FORMAL, casual, Style.FORMAL))
        val poetic = ToneAnalysis.Analysis(Style.POETIC, Listener.PARTNER, Situation.FEELINGS)
        assertEquals(Style.POETIC, ToneAnalysis.decide(Style.FORMAL, poetic, Style.FORMAL))
    }

    @Test fun theWorkedExamplesAreInTheShapeTheParserReads() {
        for ((_, reply) in ToneAnalysis.EXAMPLES) assertNotNull("example reply: $reply", ToneAnalysis.parse(reply))
        // and they cover every tone the model can pick
        val tones = ToneAnalysis.EXAMPLES.mapNotNull { ToneAnalysis.parse(it.second)?.tone }.toSet()
        assertTrue("tones in the examples: $tones", tones.containsAll(setOf(Style.FORMAL, Style.RESPECTFUL, Style.FUNNY, Style.POETIC, Style.CASUAL)))
    }

    @Test fun situationsFindExamplesInTheBanks() {
        fun count(style: Style, situation: Situation) =
            Bank.parseGrouped(ToneBank.forStyle(style)!!).count { ToneAnalysis.headingMatches(situation, it.group) }
        assertTrue(count(Style.POETIC, Situation.FEELINGS) >= 2)
        assertTrue(count(Style.POETIC, Situation.CARE) >= 2)
        assertTrue(count(Style.FUNNY, Situation.TEASING) >= 2)
        assertTrue(count(Style.FUNNY, Situation.WORK) >= 2)
        for (s in listOf(Situation.GREETING, Situation.REQUEST, Situation.THANKS, Situation.CARE, Situation.WISHES)) {
            assertTrue("respectful $s", count(Style.RESPECTFUL, s) >= 2)
        }
    }

    @Test fun groupedParsingKeepsHeadingsAndMatchesPlainParsing() {
        val text = ToneBank.POETIC
        val grouped = Bank.parseGrouped(text)
        assertEquals(Bank.parse(text).size, grouped.size)
        assertEquals("longing", grouped.first().group)
        assertTrue(grouped.none { it.group.contains("love", ignoreCase = true) })
    }

    @Test fun keywordGuessSaysWhenItIsSure() {
        assertTrue(ToneGuess.detailed("Please give my salaam to Mother.", Style.CASUAL).confident)
        assertTrue(ToneGuess.detailed("Haha you snore like a train, lol", Style.CASUAL).confident)
        assertTrue(ToneGuess.detailed("ok thanks", Style.CASUAL).confident)
        // no clue in the words and long enough to be worth asking the model
        val plain = ToneGuess.detailed("Could you tell me when the next appointment is available?", Style.CASUAL)
        assertFalse(plain.confident)
        assertEquals(Style.CASUAL, plain.style)
    }
}
