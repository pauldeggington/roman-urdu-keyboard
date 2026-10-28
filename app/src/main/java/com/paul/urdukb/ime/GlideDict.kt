package com.paul.urdukb.ime

import android.content.Context

/** The English word list for glide typing, loaded once in the background. */
object GlideDict {
    @Volatile var decoder: GlideDecoder? = null
        private set

    @Synchronized
    fun load(ctx: Context) {
        if (decoder != null) return
        try {
            ctx.applicationContext.assets.open("dict/en.tsv").bufferedReader().use { r -> decoder = GlideDecoder.parse(r.lineSequence()) }
        } catch (_: Exception) {
        }
    }
}
