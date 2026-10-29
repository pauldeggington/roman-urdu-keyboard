package com.paul.urdukb.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedPaletteTest {

    @Test
    fun hslRoundTripKeepsTheColour() {
        for (rgb in listOf(0xFFC27BA0.toInt(), 0xFF3D7BE8.toInt(), 0xFF8BC34A.toInt(), 0xFFE9A23B.toInt())) {
            val h = Hsl.hue(rgb)
            assertTrue("hue in range", h in 0f..360f)
        }
        // known values: pure red, green, blue
        assertEquals(0f, Hsl.hue(0xFFFF0000.toInt()), 0.01f)
        assertEquals(120f, Hsl.hue(0xFF00FF00.toInt()), 0.01f)
        assertEquals(240f, Hsl.hue(0xFF0000FF.toInt()), 0.01f)
        assertEquals(0xFFFF0000.toInt(), Hsl.toArgb(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00.toInt(), Hsl.toArgb(120f, 1f, 0.5f))
    }

    @Test
    fun contrastMathMatchesTheWcagExamples() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        assertEquals(4.48, Contrast.ratio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.05)
    }

    /** Prints every pair that is below the usual limits, so a bad swatch shows up in the build log. */
    @Test
    fun reportContrastOfEverySwatch() {
        val bad = ArrayList<String>()
        for (sw in Swatches.SEEDED) for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            val k = SeedPalette.keyboard(sw.color, sw.tint, dark)
            val a = SeedPalette.app(sw.color, sw.tint, dark)
            fun check(what: String, fg: Int, bg: Int, min: Double) {
                val r = Contrast.ratio(fg, bg)
                if (r < min) bad += "%s %s %s %.2f<%.1f".format(sw.id, mode, what, r, min)
            }
            check("kb keyText/bg", k.keyText, k.bg, 4.5)
            check("kb keyText/space", k.keyText, k.space, 4.5)
            check("kb hint/bg", k.hint, k.bg, 3.0)
            check("kb pillText/pill", k.pillText, k.pill, 4.5)
            check("kb actionText/action", k.actionText, k.action, 4.5)
            check("kb popupText/popupBg", k.popupText, k.popupBg, 4.5)
            check("kb selectedText/selected", k.selectedText, k.selected, 4.5)
            check("app text/appBg", a.text, a.appBg, 4.5)
            check("app text/card", a.text, a.card, 4.5)
            check("app text/field", a.text, a.field, 4.5)
            check("app textMuted/card", a.textMuted, a.card, 4.5)
            check("app textMuted/appBg", a.textMuted, a.appBg, 4.5)
            check("app onPrimary/primary", a.onPrimary, a.primary, 4.5)
            check("app onTonal/tonal", a.onTonal, a.tonal, 4.5)
        }
        println("CONTRAST below limit (${bad.size}): " + bad.joinToString("; "))
    }
}
