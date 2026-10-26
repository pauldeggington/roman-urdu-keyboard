package com.paul.urdukb.ime

enum class KeyType { CHAR, SHIFT, DELETE, SYMBOLS, EMOJI, SPACE, ENTER, SPACER }

enum class Page { LETTERS, SYMBOLS1, SYMBOLS2 }

/** One key. [weight] is its width in tenths of the keyboard width. */
class KeyDef(
    val type: KeyType,
    val label: String = "",
    val weight: Float = 1f,
    val popups: List<String> = emptyList(),
    val hint: String? = null,
    val target: Page? = null,
)

/**
 * Gboard's layout, with Gboard's key widths measured from a Pixel 8a screenshot:
 * top row 10 keys, middle row 9 keys with half-key margins, bottom letter row with 1.5-key shift and delete.
 */
object Layouts {

    private val accents: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "c" to listOf("ç", "ć", "č"),
        "d" to listOf("ð"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("î", "ï", "í", "ī", "į", "ì"),
        "l" to listOf("ł"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ô", "ö", "ò", "ó", "œ", "ø", "ō", "õ"),
        "s" to listOf("ß", "ś", "š"),
        "u" to listOf("û", "ü", "ù", "ú", "ū"),
        "y" to listOf("ÿ", "ý"),
        "z" to listOf("ž", "ź", "ż"),
    )

    private val symbolPopups: Map<String, List<String>> = mapOf(
        "1" to listOf("¹", "½", "⅓", "¼", "⅛"), "2" to listOf("²", "⅔"), "3" to listOf("³", "¾", "⅜"), "4" to listOf("⁴"),
        "5" to listOf("⅝"), "6" to listOf("⁶"), "7" to listOf("⅞", "⁷"), "8" to listOf("⁸"), "9" to listOf("⁹"),
        "0" to listOf("º", "∅", "ⁿ"),
        "-" to listOf("–", "—", "•"), "_" to listOf("‿"),
        "$" to listOf("¢", "£", "€", "¥", "₹"),
        "\"" to listOf("“", "”", "„", "«", "»"), "'" to listOf("‘", "’", "‚", "‹", "›"),
        "!" to listOf("¡"), "?" to listOf("¿"),
        "(" to listOf("[", "{", "<"), ")" to listOf("]", "}", ">"),
        "/" to listOf("\\"), "+" to listOf("±"), "=" to listOf("≈", "≠", "≤", "≥"),
        "*" to listOf("†", "‡", "★"), "&" to listOf("§"), "#" to listOf("№"), "%" to listOf("‰"),
        ":" to listOf("…"), ";" to listOf("…"),
    )

    private val periodPopups = listOf("!", "?", "'", "\"", ":", ";", "-", "(", ")", "/", "@", "&", "…")

    private fun letter(c: String, digit: String?): KeyDef {
        val pops = buildList {
            if (digit != null) add(digit)
            addAll(accents[c].orEmpty())
        }
        return KeyDef(KeyType.CHAR, c, 1f, pops, hint = digit)
    }

    private fun sym(c: String) = KeyDef(KeyType.CHAR, c, 1f, symbolPopups[c].orEmpty())

    private fun bottom(symbolsLabel: String, target: Page) = listOf(
        KeyDef(KeyType.SYMBOLS, symbolsLabel, 1.57f, target = target),
        KeyDef(KeyType.CHAR, ",", 1f),
        KeyDef(KeyType.EMOJI, "", 1f),
        KeyDef(KeyType.SPACE, "", 3.84f),
        KeyDef(KeyType.CHAR, ".", 1f, periodPopups),
        KeyDef(KeyType.ENTER, "", 1.59f),
    )

    private fun row(chars: String, digits: String? = null) =
        chars.mapIndexed { i, c -> letter(c.toString(), digits?.getOrNull(i)?.toString()) }

    private fun symRow(chars: List<String>) = chars.map { sym(it) }

    fun rows(page: Page): List<List<KeyDef>> = when (page) {
        Page.LETTERS -> listOf(
            row("qwertyuiop", "1234567890"),
            listOf(KeyDef(KeyType.SPACER, weight = 0.5f)) + row("asdfghjkl") + KeyDef(KeyType.SPACER, weight = 0.5f),
            listOf(KeyDef(KeyType.SHIFT, weight = 1.5f)) + row("zxcvbnm") + KeyDef(KeyType.DELETE, weight = 1.5f),
            bottom("?123", Page.SYMBOLS1),
        )
        Page.SYMBOLS1 -> listOf(
            symRow("1234567890".map { it.toString() }),
            symRow(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")),
            listOf(KeyDef(KeyType.SYMBOLS, "=\\<", 1.5f, target = Page.SYMBOLS2)) +
                symRow(listOf("*", "\"", "'", ":", ";", "!", "?")) + KeyDef(KeyType.DELETE, weight = 1.5f),
            bottom("ABC", Page.LETTERS),
        )
        Page.SYMBOLS2 -> listOf(
            symRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")),
            symRow(listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")),
            listOf(KeyDef(KeyType.SYMBOLS, "?123", 1.5f, target = Page.SYMBOLS1)) +
                symRow(listOf("%", "©", "®", "™", "✓", "[", "]")) + KeyDef(KeyType.DELETE, weight = 1.5f),
            bottom("ABC", Page.LETTERS),
        )
    }
}
