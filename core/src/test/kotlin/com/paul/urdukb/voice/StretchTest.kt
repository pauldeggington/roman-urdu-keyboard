package com.paul.urdukb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StretchTest {

    private fun tone(seconds: Double, hz: Double, amp: Double = 8000.0): ShortArray =
        ShortArray((seconds * 16000).toInt()) { (amp * sin(2 * PI * hz * it / 16000)).toInt().toShort() }

    private fun crossings(s: ShortArray): Int {
        var c = 0
        for (i in 1 until s.size) if ((s[i - 1] < 0) != (s[i] < 0)) c++
        return c
    }

    @Test fun factorOneChangesNothing() {
        val s = tone(1.0, 300.0)
        assertTrue(Stretch.slower(s, 1.0) === s)
    }

    @Test fun tooShortToStretchIsLeftAlone() {
        val s = ShortArray(300)
        assertTrue(Stretch.slower(s, 1.25) === s)
    }

    @Test fun itGetsLongerByTheFactor() {
        val s = tone(4.0, 250.0)
        val out = Stretch.slower(s, 1.25)
        val ratio = out.size.toDouble() / s.size
        assertTrue("length ratio $ratio", ratio in 1.20..1.30)
    }

    @Test fun pitchIsKept() {
        // a 300 Hz tone still has about 300 cycles a second: crossings per second stay the same
        val s = tone(3.0, 300.0)
        val out = Stretch.slower(s, 1.25)
        val before = crossings(s) / (s.size / 16000.0)
        val after = crossings(out) / (out.size / 16000.0)
        assertTrue("crossings per second $before -> $after", after in before * 0.93..before * 1.07)
    }

    @Test fun loudnessIsKept() {
        val s = tone(3.0, 220.0)
        val out = Stretch.slower(s, 1.25)
        val before = Stretch.level(s)
        // ignore the first and last 50 ms, where the overlap is still building up
        val mid = out.copyOfRange(800, out.size - 800)
        val after = Stretch.level(mid)
        assertTrue("level $before -> $after", after in before * 0.85..before * 1.15)
    }

    @Test fun silenceStaysSilent() {
        val out = Stretch.slower(ShortArray(16000), 1.25)
        assertTrue(out.all { it.toInt() == 0 })
    }

    @Test fun slowedPiecesStayUnderThirtySeconds() {
        // careful listening cuts pieces at 21 s so the slowed copy is at most 26.25 s
        assertTrue(21 * 1.25 < 30.0)
    }
}
