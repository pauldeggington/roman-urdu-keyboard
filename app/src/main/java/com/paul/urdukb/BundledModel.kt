package com.paul.urdukb

import android.content.Context
import android.content.res.AssetManager
import android.os.StatFs
import java.io.File

/**
 * Gemma 3 1B can ship inside the app (GitHub APK: in the assets; Google Play: in an install-time asset pack, which
 * Android also serves through the assets). It is copied once to where the app keeps its model, so it never needs a download.
 */
object BundledModel {
    private const val DIR = "models"
    private const val NAME = "gemma3-1b-it-int4.litertlm"
    private const val SIZE = 584_417_280L

    @Volatile private var found: Boolean? = null

    /** True when this build carries the model. */
    fun available(ctx: Context): Boolean = found ?: runCatching {
        ctx.assets.list(DIR)?.contains(NAME) == true
    }.getOrDefault(false).also { found = it }

    /** The built-in model is the Gemma 3 1B one, so it only counts when that model is the chosen one. */
    fun wanted(ctx: Context): Boolean = available(ctx) && Prefs.modelChoice(ctx) == ModelChoice.GEMMA3 && !LlmEngine.modelExists(ctx)

    /** Copies it to [dest]. Throws if the phone is short of space or the copy is incomplete. */
    fun install(ctx: Context, dest: File, onProgress: (Long, Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val free = runCatching { StatFs(dest.parentFile!!.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < SIZE + 200_000_000L) throw java.io.IOException("Not enough free space for the built-in model")
        val part = File(dest.path + ".part")
        part.delete()
        var done = 0L
        ctx.assets.open("$DIR/$NAME", AssetManager.ACCESS_STREAMING).use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - last >= 8L shl 20) { last = done; onProgress(done, SIZE) }
                }
            }
        }
        if (done != SIZE) { part.delete(); throw java.io.IOException("The built-in model copy was incomplete ($done bytes)") }
        dest.delete()
        if (!part.renameTo(dest)) { part.delete(); throw java.io.IOException("Could not place the built-in model") }
        Prefs.setModelSize(ctx, SIZE)
        Prefs.setVerifiedStamp(ctx, Prefs.stamp(dest))
        Prefs.setDamagedStamp(ctx, "")
        onProgress(SIZE, SIZE)
    }
}
