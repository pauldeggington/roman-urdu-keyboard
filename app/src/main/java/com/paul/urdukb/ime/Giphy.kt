package com.paul.urdukb.ime

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.paul.urdukb.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/** One GIF or sticker from GIPHY. */
class GifItem(
    val id: String,
    val title: String,
    val thumbUrl: String,
    val fullUrl: String,
    val pageUrl: String,
    val width: Int,
    val height: Int,
    val sticker: Boolean,
)

class GiphyException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, BAD_KEY, LIMIT, NETWORK }
}

/**
 * GIFs and stickers from GIPHY (Google closed the Tenor API on 30 June 2026).
 * The only thing sent is the search word and the key; nothing from the text box.
 */
object GiphyApi {
    private const val BASE = "https://api.giphy.com/v1"
    private val cache = LruCache<String, List<GifItem>>(40)

    /** Emulator preview builds have no key, so they show made-up colour tiles to check the layout. */
    private fun previewFake(sticker: Boolean): List<GifItem> {
        val colours = listOf("e57373", "f06292", "ba68c8", "7986cb", "4fc3f7", "4db6ac", "aed581", "ffd54f", "ff8a65", "a1887f", "90a4ae", "9575cd")
        val sizes = listOf(200 to 150, 200 to 260, 200 to 200, 200 to 120, 200 to 230, 200 to 180)
        return List(24) { i ->
            val (w, h) = if (sticker) 200 to 200 else sizes[i % sizes.size]
            GifItem("preview$i", "Sample", "preview:${colours[i % colours.size]}", "preview:${colours[i % colours.size]}", "https://giphy.com", w, h, sticker)
        }
    }

    fun hasKey(ctx: Context) = Kb.giphyKey(ctx).isNotEmpty() || BuildConfig.PREVIEW

    /** Blocking: call from a background thread. [query] null or empty means trending. */
    fun fetch(ctx: Context, sticker: Boolean, query: String?, offset: Int = 0): List<GifItem> {
        val key = Kb.giphyKey(ctx)
        if (key.isEmpty()) {
            if (BuildConfig.PREVIEW) return previewFake(sticker)
            throw GiphyException(GiphyException.Kind.NO_KEY, "No key")
        }
        val q = query?.trim().orEmpty()
        val cacheKey = "${if (sticker) "s" else "g"}|$q|$offset"
        cache.get(cacheKey)?.let { return it }
        val kind = if (sticker) "stickers" else "gifs"
        val path = if (q.isEmpty()) "$kind/trending" else "$kind/search"
        val url = "$BASE/$path?api_key=${URLEncoder.encode(key, "UTF-8")}&limit=30&offset=$offset&rating=pg-13" +
            (if (q.isEmpty()) "" else "&q=${URLEncoder.encode(q, "UTF-8")}")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
        }
        try {
            when (val code = try { conn.responseCode } catch (e: Exception) { throw GiphyException(GiphyException.Kind.NETWORK, e.message ?: "network") }) {
                200 -> {}
                401, 403 -> throw GiphyException(GiphyException.Kind.BAD_KEY, "Key rejected ($code)")
                429 -> throw GiphyException(GiphyException.Kind.LIMIT, "Rate limit")
                else -> throw GiphyException(GiphyException.Kind.NETWORK, "HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONArray("data")
            val out = ArrayList<GifItem>()
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                val images = o.optJSONObject("images") ?: continue
                val small = images.optJSONObject("fixed_width_small") ?: images.optJSONObject("fixed_width") ?: continue
                val thumb = small.optString("webp").ifEmpty { small.optString("url") }
                if (thumb.isEmpty()) continue
                var full = ""
                for (name in listOf("downsized_medium", "downsized", "fixed_height", "fixed_width", "original")) {
                    val u = images.optJSONObject(name)?.optString("url").orEmpty()
                    if (u.isNotEmpty()) { full = u; break }
                }
                if (full.isEmpty()) continue
                val w = small.optString("width").toIntOrNull() ?: 200
                val h = small.optString("height").toIntOrNull() ?: 200
                val id = o.optString("id")
                out += GifItem(id, o.optString("title"), thumb, full, o.optString("url").ifEmpty { "https://giphy.com/gifs/$id" }, w, h, sticker)
            }
            cache.put(cacheKey, out)
            return out
        } finally {
            conn.disconnect()
        }
    }
}

/** Downloads and caches the pictures, and puts them on screen. */
object MediaCache {
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LruCache<String, ByteArray>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    private fun fetchBytes(url: String): ByteArray? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 20000 }
        try { if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
    } catch (_: Exception) { null }

    /** Fills [iv] with the picture at [url]; animated pictures play. Safe to call again while the view is reused. */
    fun show(iv: ImageView, url: String, rounded: Boolean = true) {
        iv.setTag(com.paul.urdukb.R.id.media_url, url)
        iv.setImageDrawable(null)
        if (url.startsWith("preview:")) {
            iv.setImageDrawable(ColorDrawable(0xFF000000.toInt() or url.removePrefix("preview:").toLong(16).toInt()))
            return
        }
        pool.execute {
            // the row may have been scrolled away and reused for another picture while this waited in line
            if (iv.getTag(com.paul.urdukb.R.id.media_url) != url) return@execute
            val bytes = memory.get(url) ?: fetchBytes(url)?.also { memory.put(url, it) } ?: return@execute
            val drawable: Drawable? = try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
            } catch (_: Exception) { null }
            main.post {
                if (iv.getTag(com.paul.urdukb.R.id.media_url) != url || drawable == null) return@post
                iv.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; start() }
            }
        }
    }

    fun release(iv: ImageView) {
        (iv.drawable as? AnimatedImageDrawable)?.stop()
        iv.setTag(com.paul.urdukb.R.id.media_url, null)
        iv.setImageDrawable(null)
    }

    /** Saves the full-size picture in the cache so another app can read it. Blocking; null when it fails. */
    fun download(ctx: Context, item: GifItem): File? {
        val dir = File(ctx.cacheDir, "gifs").apply { mkdirs() }
        val f = File(dir, "${item.id.ifEmpty { item.fullUrl.hashCode().toString() }}.gif")
        if (f.exists() && f.length() > 0) return f
        val bytes = fetchBytes(item.fullUrl) ?: return null
        f.writeBytes(bytes)
        // keep the folder small
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(25)?.forEach { it.delete() }
        return f
    }
}
