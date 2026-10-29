package com.paul.urdukb.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelsTest {
    @Test
    fun recentMediaSurvivesAStringRoundTrip() {
        val item = GifItem("abc123", "Happy\tdance\nnow", "https://m/t.webp", "https://m/f.gif", "https://giphy.com/gifs/abc123", 200, 150, false)
        val back = RecentMedia.decode(RecentMedia.encode(item))
        assertNotNull(back)
        assertEquals("abc123", back!!.id)
        assertEquals("Happy dance now", back.title)
        assertEquals("https://m/f.gif", back.fullUrl)
        assertEquals(150, back.height)
        assertFalse(back.sticker)
        assertTrue(RecentMedia.decode(RecentMedia.encode(GifItem("s1", "", "t", "f", "p", 1, 1, true)))!!.sticker)
        assertNull(RecentMedia.decode("junk"))
    }

    @Test
    fun clipAgeReadsNaturally() {
        val now = 10_000_000_000L
        assertEquals("just now", ClipStore.ago(now, now - 20_000))
        assertEquals("5 min ago", ClipStore.ago(now, now - 5 * 60_000))
        assertEquals("3 h ago", ClipStore.ago(now, now - 3 * 3_600_000L))
        assertEquals("2 d ago", ClipStore.ago(now, now - 2 * 86_400_000L))
        assertEquals("a week", ClipStore.ttlWords(168))
        assertEquals("an hour", ClipStore.ttlWords(1))
    }

    @Test
    fun versionIsShownWithItsBuild() {
        assertEquals("2.1 (build 121)", com.paul.urdukb.Updater.display("2.1.121"))
        assertEquals("2.1 (build 121)-test", com.paul.urdukb.Updater.display("2.1.121-test"))
        assertEquals("2.0-dev", com.paul.urdukb.Updater.display("2.0-dev"))
    }
}
