package com.paul.urdukb

/** The three ways the model can translate. */
enum class Style(val key: String) {
    CASUAL("casual"),      // English to Roman Urdu, tum
    FORMAL("formal"),      // English to Roman Urdu, aap
    TO_ENGLISH("english"), // Roman Urdu to English
}
