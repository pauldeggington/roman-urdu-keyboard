package com.paul.urdukb.ui

import androidx.compose.ui.graphics.Color

/**
 * The app's colours: the same deep green and gold as the website (pauldeggington.github.io/roman-urdu-keyboard).
 * The app is always dark; the keyboard keeps its own light, dark and swatch choices (see KeyboardLook).
 */
object Brand {
    val page = Color(0xFF070F0C)        // behind everything
    val card = Color(0xFF0D1F18)        // cards and sheets
    val raised = Color(0xFF122B21)      // a surface on a card: chips, fields, the unfilled pill
    val raised2 = Color(0xFF17382B)     // the pressed or selected version of that
    val line = Color(0x14FFFFFF)        // hairlines: card borders, dividers
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFF94969C)       // secondary text, captions
    val faint = Color(0xFF61646C)       // disabled text
    val gold = Color(0xFFE9B73A)        // the accent: progress, selected, highlights
    val gold2 = Color(0xFFFFD76A)       // brighter gold for glows and small marks
    val onGold = Color(0xFF1B1A14)      // text on a gold surface
    val green = Color(0xFF1F8A5C)       // "done", success
    val green2 = Color(0xFF7FD1A8)      // success text on dark
    val ember = Color(0xFFDC6803)       // a warm second accent in glows
    val error = Color(0xFFF2B8B5)
    val pillBg = Color(0xFFFFFFFF)      // the main button is a white pill, like the website
    val pillText = Color(0xFF161B26)
    val pillIconBg = Color(0xFF161B26)  // the round dark badge at the end of the main button
}
