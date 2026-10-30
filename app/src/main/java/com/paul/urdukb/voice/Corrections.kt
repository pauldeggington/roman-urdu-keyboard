package com.paul.urdukb.voice

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Remembers, on the phone only, what the model heard and what the person fixed it to. It is never uploaded; the person can
 * share the file to send real examples for testing and tuning.
 */
object Corrections {

    private const val MAX_LINES = 300

    fun file(ctx: Context): File = File(File(ctx.filesDir, "voice-data").apply { mkdirs() }, "voice-corrections.jsonl")

    @Synchronized
    fun save(ctx: Context, heard: String, corrected: String) {
        if (heard.isBlank() || corrected.isBlank() || heard == corrected) return
        val line = JSONObject().put("time", System.currentTimeMillis() / 1000).put("heard", heard).put("corrected", corrected).toString()
        val f = file(ctx)
        val lines = if (f.exists()) f.readLines().takeLast(MAX_LINES - 1) else emptyList()
        f.writeText((lines + line).joinToString("\n") + "\n")
    }

    fun count(ctx: Context): Int = file(ctx).takeIf { it.exists() }?.readLines()?.count { it.isNotBlank() } ?: 0
}
