package com.paul.urdukb

import android.content.Context
import java.io.FileNotFoundException

/**
 * Spelling variants of Roman Urdu words: "variant -> most common spelling".
 * Data: RUWV-NSR (Ahmed, M., 2024, Mendeley Data, doi:10.17632/v5jfhsvtmd.5, CC BY 4.0), converted by tools/convert_ruwv.py.
 * The file is optional: without it everything below still works with the built-in shorthand list only.
 */
object VariantMap {

    @Volatile
    private var cache: Map<String, String>? = null

    fun get(ctx: Context): Map<String, String> = cache ?: load(ctx).also { cache = it }

    fun available(ctx: Context): Boolean = get(ctx).isNotEmpty()

    private fun load(ctx: Context): Map<String, String> {
        val out = HashMap<String, String>(32_768)
        try {
            ctx.applicationContext.assets.open("roman_urdu/variants.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val i = line.indexOf('\t')
                    if (i <= 0) continue
                    val variant = line.substring(0, i).trim()
                    val common = line.substring(i + 1).trim()
                    if (variant.isNotEmpty() && common.isNotEmpty()) out[variant] = common
                }
            }
        } catch (_: FileNotFoundException) {
            // data file not installed: fine
        } catch (_: Throwable) {
        }
        return out
    }
}

object Spelling {

    private val TOKEN = Regex("[\\p{L}']+")

    private fun matchCase(source: String, replacement: String): String = when {
        source.length > 1 && source.all { it.isUpperCase() } -> replacement.uppercase()
        source.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }
        else -> replacement
    }

    /** For Roman Urdu input: spell out shorthand, then map spelling variants to the common spelling. */
    fun normalizeInput(ctx: Context, text: String): String {
        val expanded = Shorthand.expand(text)
        val variants = VariantMap.get(ctx)
        if (variants.isEmpty()) return expanded
        return TOKEN.replace(expanded) { m ->
            val w = m.value
            val key = w.lowercase()
            val common = variants[key]
            if (common == null || common == key || w.length < 3 || Detect.isEnglishWord(key)) w else matchCase(w, common)
        }
    }

    /**
     * For Roman Urdu output: fix odd spellings to the common one, but never touch your own style.
     * Left alone: anything you wrote in the glossary (either side), words ending in "ay", English words,
     * and any change that would land on a word your glossary rewrites.
     */
    fun checkOutput(ctx: Context, text: String, glossary: List<Pair<String, String>>): String {
        val variants = VariantMap.get(ctx)
        if (variants.isEmpty() || text.isEmpty()) return text
        val mine = HashSet<String>()
        for ((from, to) in glossary) {
            mine += from.lowercase()
            mine += to.lowercase()
        }
        return TOKEN.replace(text) { m ->
            val w = m.value
            val key = w.lowercase()
            val common = variants[key]
            when {
                common == null || common == key -> w
                w.length < 3 -> w
                key.endsWith("ay") -> w
                key in mine || common.lowercase() in mine -> w
                Detect.isEnglishWord(key) -> w
                else -> matchCase(w, common)
            }
        }
    }
}
