package com.paul.urdukb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PcmTest {

    private fun tone(seconds: Double, rate: Int, hz: Double = 440.0, amp: Double = 8000.0): ShortArray =
        ShortArray((seconds * rate).toInt()) { (amp * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    @Test fun sixteenKHzMonoPassesThrough() {
        val s = tone(1.0, 16000)
        assertEquals(s.toList(), Pcm.toMono16k(s, 16000, 1).toList())
    }

    @Test fun fortyEightKHzBecomesSixteenAndKeepsItsLength() {
        val s = tone(2.0, 48000)
        val out = Pcm.toMono16k(s, 48000, 1)
        assertEquals(32000, out.size)
        // a 440 Hz tone is still a tone, at about the same level
        assertTrue("level ${Pcm.peak(out)}", Pcm.peak(out) in 0.2f..0.3f)
    }

    @Test fun stereoIsAveragedToMono() {
        val interleaved = ShortArray(2000) { if (it % 2 == 0) 1000 else 3000 }
        val out = Pcm.toMono16k(interleaved, 16000, 2)
        assertEquals(1000, out.size)
        assertTrue(out.all { it.toInt() == 2000 })
    }

    @Test fun lowerRatesAreStretched() {
        val out = Pcm.toMono16k(tone(1.0, 8000), 8000, 1)
        assertEquals(16000, out.size)
    }

    @Test fun silenceIsDetected() {
        assertTrue(Pcm.isSilent(ShortArray(16000)))
        assertTrue(Pcm.isSilent(ShortArray(0)))
        assertFalse(Pcm.isSilent(tone(1.0, 16000)))
    }

    @Test fun shortRecordingsAreOnePiece() {
        assertEquals(1, Pcm.split(tone(20.0, 16000)).size)
    }

    @Test fun longRecordingsAreCutUnderThirtySecondsAtAQuietMoment() {
        // 70 seconds of tone with a 0.4 s gap near 26 s and near 54 s
        val s = tone(70.0, 16000)
        for (gapAt in listOf(26.0, 54.0)) {
            val from = (gapAt * 16000).toInt()
            for (i in from until from + 6400) s[i] = 0
        }
        val pieces = Pcm.split(s)
        assertTrue("pieces: ${pieces.map { Pcm.seconds(it) }}", pieces.size in 3..4)
        assertTrue(pieces.all { Pcm.seconds(it) < 30.0 })
        // nothing is lost
        assertEquals(s.size, pieces.sumOf { it.size })
        // the first cut falls in the gap, not through the tone
        val firstCutSec = pieces[0].size / 16000.0
        assertTrue("first cut at $firstCutSec", firstCutSec in 26.0..26.5)
    }

    @Test fun aTinyLeftoverJoinsThePieceBefore() {
        val s = tone(28.3, 16000)
        val pieces = Pcm.split(s)
        assertEquals(s.size, pieces.sumOf { it.size })
        assertTrue(pieces.all { Pcm.seconds(it) < 30.0 })
    }

    @Test fun wavHasAProperHeader() {
        val s = ShortArray(100) { it.toShort() }
        val w = Pcm.wav(s)
        assertEquals(44 + 200, w.size)
        assertEquals("RIFF", String(w, 0, 4))
        assertEquals("WAVE", String(w, 8, 4))
        assertEquals("data", String(w, 36, 4))
        fun int(at: Int) = (w[at].toInt() and 255) or ((w[at + 1].toInt() and 255) shl 8) or ((w[at + 2].toInt() and 255) shl 16) or ((w[at + 3].toInt() and 255) shl 24)
        assertEquals(36 + 200, int(4))
        assertEquals(16000, int(24))
        assertEquals(200, int(40))
        // first sample after the header is 0, the second is 1, little-endian
        assertEquals(1, w[46].toInt())
    }

    @Test fun rawHasNoHeader() {
        val r = Pcm.raw(shortArrayOf(1, -2))
        assertEquals(listOf<Byte>(1, 0, -2, -1), r.toList())
    }

    @Test fun cleanTrimsSilenceAndKeepsRoomAroundSpeech() {
        val body = tone(1.0, 16000)
        val s = ShortArray(16000) + body + ShortArray(16000)
        val out = Pcm.clean(s)
        val seconds = out.size / 16000.0
        assertTrue("trimmed to $seconds s", seconds in 1.3..1.7)
    }

    @Test fun cleanRaisesAQuietRecordingButNotTooMuch() {
        val quiet = tone(2.0, 16000, amp = 500.0)
        val out = Pcm.clean(quiet)
        val peak = Pcm.peak(out) * 32768f
        assertTrue("peak $peak", peak in 3000f..4500f)    // about 8 times, not more
    }

    @Test fun cleanLeavesALoudRecordingAlone() {
        val loud = tone(2.0, 16000, amp = 24000.0)
        val out = Pcm.clean(loud)
        assertTrue(Pcm.peak(out) * 32768f in 20000f..27000f)
    }

    @Test fun cleanRemovesAConstantOffset() {
        val s = tone(2.0, 16000, amp = 6000.0)
        for (i in s.indices) s[i] = (s[i] + 4000).coerceIn(-32768, 32767).toShort()
        val out = Pcm.clean(s)
        val mean = out.drop(4000).take(16000).map { it.toDouble() }.average()
        assertTrue("mean $mean", kotlin.math.abs(mean) < 300.0)
    }

    @Test fun cleanLeavesSilenceAndTinyClipsAlone() {
        val silent = ShortArray(32000)
        assertTrue(Pcm.clean(silent) === silent)
        val tiny = ShortArray(100) { 5 }
        assertTrue(Pcm.clean(tiny) === tiny)
    }
}
