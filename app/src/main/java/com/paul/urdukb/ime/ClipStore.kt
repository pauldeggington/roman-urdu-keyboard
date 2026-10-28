package com.paul.urdukb.ime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clipboard history, like Gboard's: text you copy is kept for an hour, a day or a week as chosen (pinned clips stay), only on this phone, and
 * only after the person turns it on. Passwords copied from a password manager are flagged sensitive and are skipped.
 */
object ClipStore {
    class Clip(val text: String, var pinned: Boolean, val time: Long)

    private const val KEY = "clipboard_clips"
    /** How long unpinned clips are kept, in hours: an hour, a day or a week. */
    val TTL_CHOICES = listOf(1, 24, 168)

    fun ttlHours(ctx: Context): Int = Kb.prefs(ctx).getInt(Kb.PREF_CLIP_TTL, 1).takeIf { it in TTL_CHOICES } ?: 1
    fun setTtlHours(ctx: Context, hours: Int) = Kb.prefs(ctx).edit().putInt(Kb.PREF_CLIP_TTL, hours).apply()
    fun ttlWords(hours: Int) = when (hours) { 24 -> "a day"; 168 -> "a week"; else -> "an hour" }

    /** "just now", "5 min ago", "3 h ago", "2 d ago". */
    fun ago(now: Long, then: Long): String {
        val s = ((now - then) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86400 -> "${s / 3600} h ago"
            else -> "${s / 86400} d ago"
        }
    }
    private const val MAX_UNPINNED = 25
    private const val MAX_LENGTH = 20_000

    fun enabled(ctx: Context) = Kb.bool(ctx, Kb.PREF_CLIP, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        Kb.prefs(ctx).edit().putBoolean(Kb.PREF_CLIP, on).apply()
        if (!on) Kb.prefs(ctx).edit().remove(KEY).apply()
    }

    private fun read(ctx: Context): MutableList<Clip> {
        val out = ArrayList<Clip>()
        val raw = Kb.prefs(ctx).getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Clip(o.getString("t"), o.optBoolean("p"), o.optLong("at"))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun write(ctx: Context, clips: List<Clip>) {
        val arr = JSONArray()
        clips.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned).put("at", it.time)) }
        Kb.prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Pinned clips first, then the newest. Old unpinned clips are dropped. */
    fun all(ctx: Context): List<Clip> {
        if (!enabled(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val clips = read(ctx)
        val ttl = ttlHours(ctx) * 60L * 60L * 1000L
        val kept = clips.filter { it.pinned || now - it.time < ttl }
        if (kept.size != clips.size) write(ctx, kept)
        return kept.sortedWith(compareByDescending<Clip> { it.pinned }.thenByDescending { it.time })
    }

    fun add(ctx: Context, text: String) {
        if (!enabled(ctx)) return
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LENGTH) return
        val clips = read(ctx)
        val old = clips.firstOrNull { it.text == t }
        clips.removeAll { it.text == t }
        clips += Clip(t, old?.pinned == true, System.currentTimeMillis())
        // keep every pinned clip and the newest of the rest
        val pinned = clips.filter { it.pinned }
        val rest = clips.filter { !it.pinned }.sortedByDescending { it.time }.take(MAX_UNPINNED)
        write(ctx, pinned + rest)
    }

    fun togglePin(ctx: Context, clip: Clip) {
        val clips = read(ctx)
        clips.firstOrNull { it.text == clip.text }?.let { it.pinned = !it.pinned }
        write(ctx, clips)
    }

    fun delete(ctx: Context, clip: Clip) = write(ctx, read(ctx).filter { it.text != clip.text })

    /** "Clear all" removes everything that is not pinned. */
    fun clearUnpinned(ctx: Context) = write(ctx, read(ctx).filter { it.pinned })

    /** Emulator preview builds only: a few made-up clips to take a screenshot. */
    fun seedForPreview(ctx: Context) {
        Kb.prefs(ctx).edit().putBoolean(Kb.PREF_CLIP, true).apply()
        val now = System.currentTimeMillis()
        write(ctx, listOf(
            Clip("Main kal subah 9 baje aaunga, tum ready rehna", true, now - 1000),
            Clip("https://github.com/pauldeggington/roman-urdu-keyboard", false, now - 60_000),
            Clip("Meeting moved to Thursday 3 pm", false, now - 120_000),
            Clip("+92 300 1234567", false, now - 180_000),
            Clip("Eid Mubarak! Allah aap ko aur aap ke ghar walon ko khushiyan ata farmaye.", false, now - 240_000),
            Clip("Order #48213 shipped", false, now - 300_000),
        ))
    }
}
