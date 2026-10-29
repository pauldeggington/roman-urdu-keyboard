package com.paul.urdukb.ime

import android.content.Context
import android.graphics.Paint
import com.paul.urdukb.R

/** Remembers the skin tone picked for each emoji, so the next tap uses it. */
object ToneMemory {
    private const val KEY = "emoji_tones"
    @Volatile private var map: HashMap<String, String>? = null

    @Synchronized
    private fun load(ctx: Context): HashMap<String, String> {
        map?.let { return it }
        val m = HashMap<String, String>()
        for (part in Kb.prefs(ctx).getString(KEY, "").orEmpty().split('|')) {
            val i = part.indexOf('=')
            if (i > 0) m[part.substring(0, i)] = part.substring(i + 1)
        }
        map = m
        return m
    }

    fun get(ctx: Context, base: String): String? = load(ctx)[base]

    @Synchronized
    fun set(ctx: Context, base: String, variant: String) {
        val m = load(ctx)
        if (variant == base) m.remove(base) else m[base] = variant
        Kb.prefs(ctx).edit().putString(KEY, m.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
    }
}

/** One emoji from the bundled list. */
class EmojiItem(val emoji: String, val skin: Boolean, val name: String)

object EmojiData {
    class Section(val id: String, val title: String, val icon: Int, val items: List<EmojiItem>)

    @Volatile private var cached: List<Section>? = null

    private val titles = linkedMapOf(
        "smileys" to ("Smileys & Emotions" to R.drawable.kb_cat_smileys),
        "people" to ("People" to R.drawable.kb_cat_people),
        "nature" to ("Animals & Nature" to R.drawable.kb_cat_nature),
        "food" to ("Food & Drink" to R.drawable.kb_cat_food),
        "travel" to ("Travel & Places" to R.drawable.kb_cat_travel),
        "activities" to ("Activities" to R.drawable.kb_cat_activities),
        "objects" to ("Objects" to R.drawable.kb_cat_objects),
        "symbols" to ("Symbols" to R.drawable.kb_cat_symbols),
        "flags" to ("Flags" to R.drawable.kb_cat_flags),
    )

    /** Only the emoji this phone can actually draw, so there are no empty boxes. */
    @Synchronized
    fun load(ctx: Context): List<Section> {
        cached?.let { return it }
        val probe = Paint()
        val byGroup = LinkedHashMap<String, MutableList<EmojiItem>>()
        ctx.assets.open("emoji/emoji.tsv").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val p = line.split('\t')
                if (p.size < 4) return@forEach
                if (!probe.hasGlyph(p[1])) return@forEach
                byGroup.getOrPut(p[0]) { ArrayList() } += EmojiItem(p[1], p[2] == "1", p[3])
            }
        }
        val out = titles.mapNotNull { (id, tv) ->
            byGroup[id]?.let { Section(id, tv.first, tv.second, it) }
        }
        cached = out
        return out
    }

    fun loadEmoticons(ctx: Context): List<Pair<String, List<String>>> =
        ctx.assets.open("emoji/emoticons.tsv").bufferedReader().readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 2) null else p[0] to p.drop(1)
        }

    /** Emoji whose name fits every word typed, best matches first; a typo in a longer word is forgiven if nothing else matches. */
    fun search(ctx: Context, query: String): List<EmojiItem> {
        val words = EmojiSearch.words(query)
        if (words.isEmpty()) return emptyList()
        val all = load(ctx).flatMap { it.items }
        for (fuzzy in listOf(false, true)) {
            val scored = ArrayList<Pair<Int, EmojiItem>>()
            for (item in all) EmojiSearch.score(item.name.lowercase(), words, fuzzy)?.let { scored += it to item }
            if (scored.isNotEmpty()) return scored.sortedBy { it.first }.map { it.second }.take(80)
        }
        return emptyList()
    }

    private val tones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

    /** Skin tone variants: the modifier goes straight after the first character. */
    fun variants(emoji: String): List<String> {
        val first = emoji.offsetByCodePoints(0, 1)
        val probe = Paint()
        return tones.map { emoji.substring(0, first) + it + emoji.substring(first) }.filter { probe.hasGlyph(it) }
    }
}
