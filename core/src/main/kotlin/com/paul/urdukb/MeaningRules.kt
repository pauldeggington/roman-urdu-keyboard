package com.paul.urdukb

/**
 * Urdu puts the verb last (subject, object, verb); English puts it after the subject. A word-for-word translation in either
 * direction reads wrongly, so the model is told to translate what is meant, not how it is built.
 */
object MeaningRules {

    /** For Roman Urdu to English. */
    const val TO_ENGLISH = "\n- Urdu puts the verb at the end of the clause (subject, object, verb) and English does not. Never translate word for word or keep the Urdu word order or phrasing: say what is meant, in natural English word order and idiom (\"main ne usay samjhaya\" is \"I explained it to him\", not \"I to him explained\")."

    /** For English to Roman Urdu. */
    const val TO_URDU = "\n- Write natural Urdu: the verb goes at the end of the clause (subject, object, verb), the way a native speaker would say it. Translate the intended meaning, not the English word order or phrasing (\"I have to go\" is \"mujhe jana hai\")."

    /** The rule for turning English into Urdu script (voice messages). */
    const val TO_URDU_SCRIPT = "\n- Write natural Urdu with the verb at the end of the clause (subject, object, verb), as a native speaker would say it. Translate the intended meaning, not the English word order or phrasing."
}
