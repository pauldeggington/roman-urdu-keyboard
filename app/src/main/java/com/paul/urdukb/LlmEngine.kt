package com.paul.urdukb

import android.content.Context
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs Gemma 4 E2B on-device through LiteRT-LM. One shared engine, loaded on first use. */
object LlmEngine {

    private var engine: Engine? = null
    private var engineIsGpu = false

    fun modelFile(ctx: Context): File = File(Prefs.store(ctx).filesDir, "model.litertlm")

    fun isLoaded(): Boolean = engine != null

    /** True only if the file is there and its size matches what we expected when it was installed. */
    fun modelExists(ctx: Context): Boolean {
        val f = modelFile(ctx)
        if (!f.exists() || f.length() == 0L) return false
        val expected = Prefs.modelSize(ctx)
        if (expected != 0L && f.length() != expected) return false
        return Prefs.damagedStamp(ctx) != Prefs.stamp(f)
    }

    fun setUseGpu(ctx: Context, value: Boolean) {
        Prefs.setGpu(ctx, value)
        reset()
    }

    /** Drop the loaded engine (after a new model is installed or the backend changes). */
    @Synchronized
    fun reset() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
    }

    private fun build(ctx: Context): Pair<Engine, Boolean> {
        val path = modelFile(ctx).absolutePath
        val errors = mutableListOf<String>()
        val backends = if (Prefs.gpu(ctx)) listOf(true, false) else listOf(false)

        for (gpu in backends) {
            for (variant in 0..2) {
                val label = (if (gpu) "GPU" else "CPU") + " config ${variant + 1}"
                try {
                    val backend = if (gpu) Backend.GPU() else Backend.CPU()
                    val config = when (variant) {
                        0 -> EngineConfig(modelPath = path, backend = backend, cacheDir = Prefs.store(ctx).cacheDir.path)
                        1 -> EngineConfig(modelPath = path, backend = backend)
                        else -> EngineConfig(
                            modelPath = path,
                            backend = backend,
                            maxNumTokens = 4096,
                            cacheDir = Prefs.store(ctx).cacheDir.path,
                        )
                    }
                    val t0 = System.nanoTime()
                    val e = Engine(config)
                    e.initialize()
                    Prefs.setLoad(ctx, if (gpu) "GPU" else "CPU", (System.nanoTime() - t0) / 1_000_000)
                    return e to gpu
                } catch (t: Throwable) {
                    errors += "$label: ${t.message}"
                }
            }
        }
        throw IllegalStateException(
            "Could not start the model (${modelFile(ctx).length() / 1048576} MB file).\n" + errors.joinToString("\n")
        )
    }

    @Synchronized
    private fun getEngine(ctx: Context): Engine {
        val wantGpu = Prefs.gpu(ctx)
        engine?.let {
            if (engineIsGpu == wantGpu || !wantGpu) return it
            reset()
        }
        val (created, usedGpu) = build(ctx)
        engine = created
        engineIsGpu = usedGpu
        return created
    }

    /** The few examples from the bank that are closest to this text. */
    private fun examples(ctx: Context, style: Style, text: String): List<Pair<String, String>> =
        Bank.pick(Bank.parse(Prefs.examples(ctx, style)), text, k = 6) { VariantMap.canon(ctx, it) }

    /** Blocking. Call from a background thread. */
    @Synchronized
    fun translate(ctx: Context, rawText: String, style: Style): String {
        val eng = getEngine(ctx)
        // Roman Urdu shorthand (kr, nhi, tm ...) is spelled out first, so the model and the example lookup see real words
        val text = if (style == Style.TO_ENGLISH) Spelling.normalizeInput(ctx, rawText) else rawText
        val t0 = System.nanoTime()
        // Spellings only matter for Roman Urdu output
        val glossary = if (style == Style.TO_ENGLISH) emptyList() else Glossary.parse(Prefs.glossary(ctx))
        val config = ConversationConfig(
            systemInstruction = Contents.of(Prefs.rules(ctx, style) + Glossary.promptBlock(glossary)),
            initialMessages = examples(ctx, style, text).flatMap { (en, ur) -> listOf(Message.user(en), Message.model(ur)) },
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
        )
        // A fresh conversation per translation, so earlier messages never leak into the next one.
        return eng.createConversation(config).use { conversation ->
            val reply = runBlocking { conversation.sendMessage(text) }
            val cleaned = clean(reply.toString())
            // Roman Urdu output: your glossary, which also fixes other spellings of the same word
            val out = if (style == Style.TO_ENGLISH) cleaned
                else Glossary.apply(glossary, cleaned) { VariantMap.cluster(ctx, it) }
            Prefs.addTranslation(ctx, (System.nanoTime() - t0) / 1_000_000)
            out
        }
    }

    private fun clean(raw: String): String {
        var s = raw.replace("<end_of_turn>", "").trim()
        for (prefix in listOf("Roman Urdu:", "English:")) {
            if (s.startsWith(prefix, ignoreCase = true)) s = s.substring(prefix.length).trim()
        }
        for (marker in listOf("\nEnglish:", "\nRoman Urdu:")) {
            val cut = s.indexOf(marker)
            if (cut >= 0) s = s.substring(0, cut).trim()
        }
        return s.trim('"', ' ', '\n')
    }

    /** Remember the full error text so the app can show and copy it. */
    fun saveError(ctx: Context, t: Throwable) {
        val sb = StringBuilder()
        sb.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 4) {
            sb.append(cur.javaClass.simpleName).append(": ").append(cur.message).append('\n')
            cur = cur.cause
            depth++
        }
        Prefs.setLastError(ctx, sb.toString().trim())
    }
}
