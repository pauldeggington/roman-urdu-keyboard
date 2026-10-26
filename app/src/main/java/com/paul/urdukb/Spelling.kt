package com.paul.urdukb

import android.content.Context
import java.io.FileNotFoundException

/**
 * Spelling variants of Roman Urdu words: "variant -> the dataset's common spelling".
 * Data: RUWV-NSR (Ahmed, M., 2024, Mendeley Data, doi:10.17632/v5jfhsvtmd.5, CC BY 4.0), converted by tools/convert_ruwv.py.
 *
 * The "common" spelling is NOT used as a spelling to write (it is often odd, e.g. shukariya, waqat). It is only used to
 * recognise that two spellings are the same word. The file is optional: without it everything still works.
 */
object VariantMap {

    @Volatile
    private var cache: Map<String, String>? = null

    @Volatile
    private var reverseCache: Map<String, List<String>>? = null

    fun get(ctx: Context): Map<String, String> = cache ?: load(ctx).also { cache = it }

    fun available(ctx: Context): Boolean = get(ctx).isNotEmpty()

    private fun reverse(ctx: Context): Map<String, List<String>> =
        reverseCache ?: HashMap<String, MutableList<String>>().also { rev ->
            for ((variant, common) in get(ctx)) rev.getOrPut(common) { mutableListOf() }.add(variant)
        }.also { reverseCache = it }

    /** A key that is the same for every spelling of a word. Only for matching, never shown or written. */
    fun canon(ctx: Context, token: String): String {
        val t = token.lowercase()
        return get(ctx)[t] ?: t
    }

    /** True if the dataset knows this as a Roman Urdu spelling variant (and it is not an English word). */
    fun isKnownVariant(ctx: Context, token: String): Boolean = get(ctx).containsKey(token.lowercase())

    /** Other spellings of the same word. */
    fun cluster(ctx: Context, word: String): List<String> {
        val m = get(ctx)
        if (m.isEmpty()) return emptyList()
        val rev = reverse(ctx)
        val w = word.lowercase()
        val out = LinkedHashSet<String>()
        rev[w]?.let { out.addAll(it) }
        m[w]?.let { common ->
            out.add(common)
            rev[common]?.let { out.addAll(it) }
        }
        out.remove(w)
        return out.toList()
    }

    private fun load(ctx: Context): Map<String, String> {
        val out = HashMap<String, String>(16_384)
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

    /** For Roman Urdu input: spell out texting shorthand (kr, nhi, tm ...). The user's spellings are otherwise left as typed. */
    fun normalizeInput(@Suppress("UNUSED_PARAMETER") ctx: Context, text: String): String = Shorthand.expand(text)
}
