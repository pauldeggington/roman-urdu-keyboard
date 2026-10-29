package com.paul.urdukb

/**
 * The ways the model can translate. The tone matrix: English to Roman Urdu in six tones, plus Roman Urdu to English.
 * [key] names the saved rules and example bank of each style.
 */
enum class Style(val key: String) {
    CASUAL("casual"),              // tum, everyday
    FORMAL("formal"),              // aap, polite
    POETIC("poetic"),              // literary, shayari-like
    FUNNY("funny"),                // playful, teasing
    AFFECTIONATE("affectionate"),  // warm, for a partner or family
    RESPECTFUL("respectful"),      // elders, extra polite
    TO_ENGLISH("english");         // Roman Urdu to English

    /** True for the six English to Roman Urdu tones. */
    val isTone: Boolean get() = this != TO_ENGLISH

    /** The original bank this tone borrows a couple of examples from, so everyday phrasing still comes out right. */
    val register: Style get() = if (this == FORMAL || this == RESPECTFUL) FORMAL else CASUAL

    /** True for the four tones that have their own example bank beyond Casual and Formal. */
    val isExtraTone: Boolean get() = this != TO_ENGLISH && this != CASUAL && this != FORMAL

    companion object {
        /** The tones in the order the toolbar chip cycles through them. */
        val TONES = listOf(CASUAL, FORMAL, POETIC, FUNNY, AFFECTIONATE, RESPECTFUL)

        fun fromKey(key: String): Style? = entries.firstOrNull { it.key == key }
    }
}
