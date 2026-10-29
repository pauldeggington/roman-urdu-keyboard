package com.paul.urdukb.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSearchTest {
    private fun s(name: String, q: String, fuzzy: Boolean = false) = EmojiSearch.score(name, EmojiSearch.words(q), fuzzy)

    @Test
    fun emojiSearchUnderstandsEverydayAndRomanUrduWords() {
        assertNotNull(s("face with tears of joy", "laugh"))
        assertNotNull(s("loudly crying face", "sad"))
        assertNotNull(s("folded hands", "shukriya"))
        assertNotNull(s("red heart", "pyaar"))
        assertNotNull(s("party popper", "mubarak"))
        assertNotNull(s("hot beverage", "chai"))
        assertNotNull(s("crescent moon", "chand"))
        assertNull(s("pizza", "sad"))
    }

    @Test
    fun emojiSearchPrefersEarlierMatchesAndForgivesATypo() {
        assertTrue(s("heart suit", "heart")!! < s("red heart", "heart")!!)
        assertNull(s("pizza", "pizaa"))
        assertEquals(3, s("pizza", "pizaa", fuzzy = true))
        assertEquals(3, s("thumbs up", "thmbs", fuzzy = true))
        assertNull(s("pizza", "cat", fuzzy = true))      // short words are never guessed
    }

    @Test
    fun closeToMeansOneEdit() {
        assertTrue(EmojiSearch.closeTo("heart", "hart"))
        assertTrue(EmojiSearch.closeTo("heart", "haert"))
        assertTrue(EmojiSearch.closeTo("heart", "heaft"))
        assertFalse(EmojiSearch.closeTo("heart", "hearts5"))
        assertFalse(EmojiSearch.closeTo("heart", "earth"))
    }
}
