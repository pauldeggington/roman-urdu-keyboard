package com.paul.urdukb.ime

import android.content.Context
import com.paul.urdukb.Detect

/** The English word list, read once in the background: it feeds glide typing and word suggestions. */
object GlideDict {
    @Volatile var decoder: GlideDecoder? = null
        private set
    @Volatile var suggester: WordSuggester? = null
        private set

    @Synchronized
    fun load(ctx: Context) {
        if (decoder != null && suggester != null) return
        try {
            val words = ctx.applicationContext.assets.open("dict/en.tsv").bufferedReader().use { WordSuggester.readWords(it.lineSequence()) }
            decoder = GlideDecoder.fromWords(words)
            // common Roman Urdu words too, so typing them is not "corrected" and they can be completed
            val known = words.mapTo(HashSet()) { it.first }
            val urdu = Detect.urduWords().filter { it !in known }.map { it to 330 }
            suggester = WordSuggester(words + urdu)
        } catch (_: Exception) {
        }
    }
}
