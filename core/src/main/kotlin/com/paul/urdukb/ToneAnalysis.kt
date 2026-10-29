package com.paul.urdukb

/**
 * What the local model works out about a sentence before it is translated: the tone, who it is to, and what it is about.
 * The keyword guess ([ToneGuess]) answers the clear cases instantly; the model is only asked when the words do not
 * settle it. Its answer is one short line, checked here before anything uses it, so a confused reply is simply ignored.
 */
object ToneAnalysis {

    /** Who the sentence is addressed to. */
    enum class Listener { ELDER, FRIEND, PARTNER, FAMILY, COLLEAGUE, STRANGER, UNKNOWN }

    /** What the sentence is about. */
    enum class Situation { GREETING, THANKS, APOLOGY, REQUEST, CARE, FEELINGS, PLANS, WORK, TEASING, WISHES, OTHER }

    class Analysis(val tone: Style?, val listener: Listener, val situation: Situation)

    const val PROMPT = """You label an English message that is about to be translated into Roman Urdu. Do not translate it and never answer it. Reply with exactly one line and nothing else:
tone=<casual|formal|poetic|funny|respectful>; listener=<elder|friend|partner|family|colleague|stranger|unknown>; situation=<greeting|thanks|apology|request|care|feelings|plans|work|teasing|wishes|other>

How to choose:
- respectful: the message is to or about parents, grandparents, teachers, elders, or anyone addressed as sir or madam.
- poetic: deep feelings, longing, beauty, nature as a metaphor, or tender words to a partner or someone loved.
- funny: jokes, banter, teasing, playful exaggeration.
- formal: work, clients, officials, or strangers.
- casual: everyday talk with friends and peers, or when nothing else fits."""

    /** A few worked examples, shown to the model before the real message. */
    val EXAMPLES: List<Pair<String, String>> = listOf(
        "Could you send me the invoice by Friday?" to "tone=formal; listener=colleague; situation=request",
        "Ammi, have you taken your medicine?" to "tone=respectful; listener=elder; situation=care",
        "You snore like a train, I swear." to "tone=funny; listener=friend; situation=teasing",
        "Every night I wait for you like the moon waits for the sun." to "tone=poetic; listener=partner; situation=feelings",
        "Are you coming to the market later?" to "tone=casual; listener=friend; situation=plans",
    )

    private val FIELD = Regex("(tone|listener|situation)\\s*[=:]\\s*([a-z]+)", RegexOption.IGNORE_CASE)

    /** Reads the model's reply. Null when it is not in the expected shape. */
    fun parse(reply: String): Analysis? {
        val found = HashMap<String, String>()
        for (m in FIELD.findAll(reply)) found.putIfAbsent(m.groupValues[1].lowercase(), m.groupValues[2].lowercase())
        if (found["tone"] == null) return null
        val tone = Style.fromKey(found.getValue("tone"))?.takeIf { it.isTone }
        val listener = Listener.entries.firstOrNull { it.name.equals(found["listener"], ignoreCase = true) } ?: Listener.UNKNOWN
        val situation = Situation.entries.firstOrNull { it.name.equals(found["situation"], ignoreCase = true) } ?: Situation.OTHER
        if (tone == null && listener == Listener.UNKNOWN) return null
        return Analysis(tone, listener, situation)
    }

    /**
     * The tone to translate in. The model's tone wins over the keyword guess, then who it is to corrects it: an elder always
     * gets the respectful tone, a colleague or stranger never gets slang. Someone whose usual tone is Formal (aap) only
     * leaves it for an elder or for poetry, as in [ToneGuess].
     */
    fun decide(keyword: Style, analysis: Analysis?, base: Style): Style {
        if (analysis == null) return keyword
        var tone = analysis.tone ?: keyword
        when (analysis.listener) {
            Listener.ELDER -> tone = Style.RESPECTFUL
            Listener.COLLEAGUE, Listener.STRANGER -> if (tone == Style.CASUAL || tone == Style.FUNNY) tone = Style.FORMAL
            else -> {}
        }
        if (base == Style.FORMAL && tone != Style.RESPECTFUL && tone != Style.POETIC) tone = Style.FORMAL
        return tone
    }

    /** Words in a bank heading that belong to each situation, so the examples shown to the model are from that situation. */
    private val HEADINGS: Map<Situation, List<String>> = mapOf(
        Situation.GREETING to listOf("greeting", "good morning"),
        Situation.THANKS to listOf("thanks", "appreciation", "friendship"),
        Situation.REQUEST to listOf("request", "permission", "invitation"),
        Situation.CARE to listOf("care", "comfort"),
        Situation.FEELINGS to listOf("longing", "devotion", "night", "hope", "sorrow", "tender", "missing"),
        Situation.TEASING to listOf("banter", "teasing", "food", "lazy", "self"),
        Situation.WORK to listOf("work", "money"),
        Situation.WISHES to listOf("occasions", "journey"),
    )

    /** True when a bank heading belongs to this situation. */
    fun headingMatches(situation: Situation, heading: String): Boolean {
        val h = heading.lowercase()
        return HEADINGS[situation].orEmpty().any { it in h }
    }
}
