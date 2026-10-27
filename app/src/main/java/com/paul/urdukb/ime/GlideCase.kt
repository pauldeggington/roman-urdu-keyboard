package com.paul.urdukb.ime

/** How a glided word is written: lower case, Capitalised after shift, or ALL CAPS on caps lock. "i" is always "I". */
object GlideCase {
    fun apply(word: String, shiftMode: Int): String {
        if (shiftMode == KeyboardView.SHIFT_CAPS) return word.uppercase()
        if (shiftMode == KeyboardView.SHIFT_ON) return word.replaceFirstChar { it.uppercaseChar() }
        return if (word == "i" || word.startsWith("i'")) "I" + word.substring(1) else word
    }
}
