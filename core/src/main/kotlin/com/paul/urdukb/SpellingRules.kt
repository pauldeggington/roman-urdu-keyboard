package com.paul.urdukb

/**
 * How Roman Urdu is spelled: by the Urdu letter, not the Hindi one. Each Urdu letter has one fixed Roman spelling, so the
 * same word looks the same every time and does not read as Hindi.
 *
 *   ڑھ (rh)   parhna, barha, charhna   (not padhna, badha, chadhna)
 *   ڑ (r)         larka, larai              (not ladka, ladai)
 *   ھ after a letter keeps its h: bh, ph, dh, th, kh, gh, jh, chh   bhai, phir, phool, dhoop, thora, ghar, jhoot, chhota
 *   ف (f)         fikar, farq, fauran, faisla   (not phikar, pharq, phauran)
 *   ز ذ ض ظ (z)  zindagi, zaroor, zyada, zaban   (not jindagi, jaroor, jyada, jaban)
 *   و (w)         waqt, wapas, wajah, wada    (not vaqt, vapas, vajah, vada)
 *
 * It is used twice: the model is told the rule, and the answer is then checked word by word against [PAIRS], so a Hindi
 * spelling that slips through is fixed for sure. The person's own glossary is applied after, and wins.
 */
object SpellingRules {

    /** One bullet for the model's rules. */
    const val PROMPT = "\n- Spelling follows the Urdu letters, never Hindi: ڑھ is rh (parhna, barha, charhna), ڑ is r (larka, larai), the aspirated letters keep their h (bhai, bhook, phir, phool, dhoop, thora, ghar, khana, jhoot, chhota), ف is f (fikar, farq, fauran, faisla), ز ذ ض ظ are z (zindagi, zaroor, zyada, zaban), و is w (waqt, wapas, wajah, wada). So never padhna, ladka, jindagi, vapas, vichar."

    /** Verb stems whose Hindi spelling uses dh where Urdu has ڑھ, with the endings they take. */
    private val RH_STEMS = mapOf(
        "padh" to "parh", "badh" to "barh", "chadh" to "charh",
    )
    private val RH_ENDINGS = listOf(
        "", "na", "ne", "ta", "ti", "te", "ni", "ana", "ata", "ati", "ate", "o", "ao", "iye", "iyega", "lo", "lena", "liya",
        "oge", "ogi", "unga", "ungi", "en", "e", "i", "a", "wa", "wana", "wane", "ai", "ayi", "ayee", "aya", "aye",
    )
    // Hindi "e" endings become Urdu "ay"; the ones that are not endings of that kind stay as they are
    private fun urduEnding(e: String) = when (e) {
        "ne" -> "nay"; "te" -> "tay"; "ate" -> "atay"; "wane" -> "wanay"; "e" -> "ay"; "aye" -> "aye"
        else -> e
    }

    /** "ladka" and the rest: r where Urdu has ڑ, and the other common Hindi-style forms. */
    private val WORDS = """
ladka -> larka
ladki -> larki
ladke -> larkay
ladkon -> larkon
ladkiyan -> larkiyan
ladkiyon -> larkiyon
ladai -> larai
ladaai -> larai
ladna -> larna
ladte -> lartay
badhai -> mubarak
badhaai -> mubarak
badhna -> barhna
badhta -> barhta
badhti -> barhti
badhte -> barhtay
badhne -> barhnay
badhao -> barhao
badhaye -> barhaye
kadhai -> karahi
fir -> phir
phikar -> fikar
pharq -> farq
phark -> farq
phauran -> fauran
phauj -> fauj
phaisla -> faisla
phaisle -> faislay
phaida -> faida
phayda -> faida
phasal -> fasal
pharmaish -> farmaish
pharmaaish -> farmaish
jindagi -> zindagi
jaroor -> zaroor
jarur -> zaroor
jaruri -> zaroori
jarurat -> zaroorat
jyada -> zyada
jyaada -> zyada
jaban -> zaban
jamana -> zamana
jameen -> zameen
jakham -> zakhm
jabardast -> zabardast
vaqt -> waqt
vakt -> waqt
vapas -> wapas
vajah -> wajah
vaja -> wajah
vada -> wada
vaada -> wada
vahan -> wahan
vahaan -> wahan
vo -> woh
voh -> woh
vishwas -> yaqeen
vichar -> khayal
vyast -> masroof
vidyarthi -> student
pariwar -> khandan
parivar -> khandan
mitr -> dost
sundar -> khoobsurat
prashn -> sawal
uttar -> jawab
shanti -> sukoon
swasth -> sehatmand
dhanyawad -> shukriya
dhanyavaad -> shukriya
kripya -> meherbani karke
samay -> waqt
kush -> khush
kushi -> khushi
khabr -> khabar
"""

    /** The word-by-word pairs: the stems below, the list above. Pairs that map a word to itself are dropped. */
    val PAIRS: List<Pair<String, String>> by lazy {
        val out = LinkedHashMap<String, String>()
        for (line in WORDS.lines()) {
            val parts = line.split("->")
            if (parts.size != 2) continue
            val a = parts[0].trim()
            val b = parts[1].trim()
            if (a.isNotEmpty() && b.isNotEmpty() && !a.equals(b, ignoreCase = true)) out[a] = b
        }
        for ((hindi, urdu) in RH_STEMS) for (end in RH_ENDINGS) {
            val a = hindi + end
            val b = urdu + urduEnding(end)
            if (!a.equals(b, ignoreCase = true)) out.putIfAbsent(a, b)
        }
        out.map { it.key to it.value }
    }
}
