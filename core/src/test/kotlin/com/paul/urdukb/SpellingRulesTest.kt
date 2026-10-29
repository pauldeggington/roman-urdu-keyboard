package com.paul.urdukb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellingRulesTest {
    private fun fix(text: String) = Glossary.apply(SpellingRules.PAIRS, text)

    @Test fun rhWordsFollowTheUrduLetter() {
        assertEquals("mujhe parhna hai", fix("mujhe padhna hai"))
        assertEquals("woh parhtay hain", fix("woh padhte hain"))
        assertEquals("Parho aur likho", fix("Padho aur likho"))
        assertEquals("mera bhai barhta ja raha hai", fix("mera bhai badhta ja raha hai"))
        assertEquals("charh jao", fix("chadh jao"))
    }

    @Test fun rHasNoDInIt() {
        assertEquals("larka aur larki", fix("ladka aur ladki"))
        assertEquals("larai nahi karo", fix("ladai nahi karo"))
    }

    @Test fun fAndPhAreKeptApart() {
        assertEquals("phir milte hain", fix("fir milte hain"))
        assertEquals("fikar mat karo", fix("phikar mat karo"))
        assertEquals("fauran aa jao", fix("phauran aa jao"))
        // Hindi-native words with a real aspirated p stay as they are
        assertEquals("phool aur phal", fix("phool aur phal"))
    }

    @Test fun zIsZ() {
        assertEquals("zindagi bohat zyada khoobsurat hai", fix("jindagi bohat jyada sundar hai"))
        assertEquals("zaroor aana", fix("jaroor aana"))
    }

    @Test fun wIsW() {
        assertEquals("wapas aa jao, waqt kam hai", fix("vapas aa jao, vaqt kam hai"))
        assertEquals("wajah batao", fix("vajah batao"))
    }

    @Test fun hindiWordsBecomeUrduWords() {
        assertEquals("sab ko mubarak ho", fix("sab ko badhai ho"))
        assertEquals("shukriya", fix("dhanyawad"))
        assertEquals("khandan", fix("pariwar"))
    }

    @Test fun rulesAreConsistent() {
        val pairs = SpellingRules.PAIRS
        assertTrue("there should be plenty of rules", pairs.size > 100)
        val keys = pairs.map { it.first.lowercase() }
        assertEquals("no word is listed twice", keys.size, keys.toSet().size)
        val targets = pairs.map { it.second.lowercase() }.toSet()
        val chained = keys.filter { it in targets }
        assertTrue("a spelling is both a mistake and a fix: $chained", chained.isEmpty())
        for ((from, to) in pairs) assertFalse("$from maps to itself", from.equals(to, ignoreCase = true))
        // every rh fix really has rh in it
        for ((from, to) in pairs) if ((from.startsWith("padh") || from.startsWith("chadh") || from.startsWith("badh")) && !from.startsWith("badhai") && !from.startsWith("badhaai")) {
            assertTrue("$from -> $to should have rh", "rh" in to)
        }
    }
}
