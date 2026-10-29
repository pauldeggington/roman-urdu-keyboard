package com.paul.urdukb.ime

/** How a glided word is written: lower case, Capitalised after shift, or ALL CAPS on caps lock. "i" is always "I". */
object GlideCase {
    /** The same numbers the keyboard uses for its shift states (off 0, on 1, caps lock 2). */
    const val SHIFT_ON = 1
    const val SHIFT_CAPS = 2

    fun apply(word: String, shiftMode: Int): String {
        if (shiftMode == SHIFT_CAPS) return word.uppercase()
        if (shiftMode == SHIFT_ON) return word.replaceFirstChar { it.uppercaseChar() }
        return if (word == "i" || word.startsWith("i'")) "I" + word.substring(1) else word
    }
}
