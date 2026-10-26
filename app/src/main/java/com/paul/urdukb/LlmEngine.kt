package com.paul.urdukb

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/** Runs the on-device Gemma model. One shared instance, loaded on first use. */
object LlmEngine {

    private const val SYSTEM_PROMPT = """You are a translator that turns English into everyday Roman Urdu, the way educated young Pakistanis speak and text in normal, polite conversation. Not slangy, not formal or literary.

Rules:
- Output only the Roman Urdu translation. No explanations, no quotes, no Urdu script.
- Address the listener as "tum", with "tumhara/tumhari/tumhare" and "tumhe". Never use "tu", "tera", "teri", "tujhe", or "aap".
- No slang or filler words like "yaar", "bhai", "oye", "bro".
- Use plain spoken Urdu. Avoid heavy Persian/Arabic words (like mulaqat, tashreef, istemal, shukriya).
- Keep common English words as they are: phone, message, call, meeting, time, plan, sorry, thanks, ok, etc.
- The speaker is male. Use masculine verb forms (karta, raha, gaya, chahta, wala tha).
- Use simple spellings: hai, nahi, kya, kyun, mein, abhi, kaafi, bhi. No diacritics.
- Keep the meaning and tone of the original. Do not add or drop information.

Examples:
English: Are you coming tonight?
Roman Urdu: Kya tum aaj raat aa rahe ho?

English: Where are you? I've been waiting for an hour.
Roman Urdu: Tum kahan ho? Main ek ghante se wait kar raha hoon.

English: Thanks, that really helped.
Roman Urdu: Thanks, is se mujhe kaafi madad mili.

English: I'll call you later, I'm in a meeting.
Roman Urdu: Main tumhe baad mein call karta hoon, abhi meeting mein hoon.

English: Is this your phone?
Roman Urdu: Kya yeh tumhara phone hai?

English: I was going to tell you.
Roman Urdu: Main tumhe batane wala tha."""

    @Volatile
    private var llm: LlmInference? = null

    fun modelFile(ctx: Context): File = File(ctx.filesDir, "model.task")

    fun modelExists(ctx: Context): Boolean = modelFile(ctx).let { it.exists() && it.length() > 0 }

    /** Drop the loaded model (call after importing a new model file). */
    @Synchronized
    fun reset() {
        llm?.close()
        llm = null
    }

    @Synchronized
    fun translate(ctx: Context, text: String): String {
        val engine = llm ?: LlmInference.createFromOptions(
            ctx.applicationContext,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile(ctx).absolutePath)
                .setMaxTokens(1024)
                .build()
        ).also { llm = it }

        val prompt = "<start_of_turn>user\n$SYSTEM_PROMPT\n\nEnglish: $text\nRoman Urdu:<end_of_turn>\n<start_of_turn>model\n"
        return clean(engine.generateResponse(prompt))
    }

    private fun clean(raw: String): String {
        var s = raw.replace("<end_of_turn>", "").trim()
        if (s.startsWith("Roman Urdu:", ignoreCase = true)) s = s.substring(11).trim()
        // Stop if the model starts inventing another example.
        val cut = s.indexOf("\nEnglish:")
        if (cut >= 0) s = s.substring(0, cut).trim()
        return s.trim('"', ' ', '\n')
    }
}
