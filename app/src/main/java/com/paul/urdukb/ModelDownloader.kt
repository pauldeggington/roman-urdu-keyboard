package com.paul.urdukb

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Downloads (with resume) or imports the model file. Blocking: call from a background thread. */
object ModelDownloader {

    class CancelledException : Exception("Cancelled")

    fun partFile(dest: File) = File(dest.path + ".part")

    /** Returns the final file size in bytes. */
    fun download(
        url: String,
        token: String,
        dest: File,
        onProgress: (done: Long, total: Long) -> Unit,
        cancelled: () -> Boolean,
    ): Long {
        val part = partFile(dest)
        var existing = if (part.exists()) part.length() else 0L
        var attempts = 0

        while (true) {
            attempts++
            val (conn, code) = open(url, token, existing)
            try {
                when (code) {
                    200, 206 -> {
                        val append = code == 206 && existing > 0
                        if (!append) existing = 0L
                        val len = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                        val total = if (len >= 0) existing + len else -1L
                        copy(conn.inputStream, part, append, existing, total, onProgress, cancelled)
                        val finalLen = part.length()
                        if (total > 0 && finalLen != total) {
                            throw IOException("Download incomplete ($finalLen of $total bytes). Tap Resume to continue.")
                        }
                        finish(part, dest)
                        return finalLen
                    }
                    416 -> {
                        part.delete()
                        existing = 0L
                        if (attempts > 2) throw IOException("The server rejected the resume request (HTTP 416).")
                    }
                    401, 403 -> throw IOException(
                        "Hugging Face refused access (HTTP $code). Check that your token has read access and that you accepted the Gemma licence on the model page."
                    )
                    404 -> throw IOException("File not found (HTTP 404). Check the model URL under Advanced.")
                    else -> throw IOException("Download failed (HTTP $code).")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Copy a file the user picked with the system file picker. Returns the final size. */
    fun importFrom(
        ctx: Context,
        uri: Uri,
        dest: File,
        onProgress: (done: Long, total: Long) -> Unit,
        cancelled: () -> Boolean,
    ): Long {
        var size = -1L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) size = it.getLong(0)
        }
        val part = partFile(dest)
        part.delete()
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Could not open that file.")
        copy(input, part, false, 0L, size, onProgress, cancelled)
        if (size > 0 && part.length() != size) {
            throw IOException("Copy incomplete (${part.length()} of $size bytes). Is the phone out of storage?")
        }
        finish(part, dest)
        return dest.length()
    }

    private fun finish(part: File, dest: File) {
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("Could not move the finished file into place.")
    }

    /** Opens the URL, following redirects by hand so the token is not sent to other hosts. */
    private fun open(url: String, token: String, offset: Long): Pair<HttpURLConnection, Int> {
        var current = URL(url)
        val originalHost = current.host
        var hops = 0
        while (true) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "RomanUrduKeyboard/0.2")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (token.isNotBlank() && current.host == originalHost) {
                c.setRequestProperty("Authorization", "Bearer $token")
            }
            if (offset > 0) c.setRequestProperty("Range", "bytes=$offset-")
            val code = c.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without a location.")
                c.disconnect()
                current = URL(current, loc)
                if (++hops > 6) throw IOException("Too many redirects.")
                continue
            }
            return c to code
        }
    }

    private fun copy(
        input: InputStream,
        part: File,
        append: Boolean,
        startOffset: Long,
        total: Long,
        onProgress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
    ) {
        FileOutputStream(part, append).use { out ->
            input.use { ins ->
                val buf = ByteArray(256 * 1024)
                var done = startOffset
                var last = 0L
                while (true) {
                    if (cancelled()) throw CancelledException()
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - last > 250_000_000L) {
                        onProgress(done, total)
                        last = now
                    }
                }
                onProgress(done, total)
            }
        }
    }

    sealed class Verdict {
        object Ok : Verdict()
        class Damaged(val detail: String) : Verdict()
        class Unknown(val reason: String) : Verdict()
    }

    /** Asks Hugging Face for the file's real size and SHA-256. Returns (sha256, size) or throws. */
    private fun expectedFromHub(url: String, token: String): Pair<String, Long> {
        val u = URL(url)
        val parts = u.path.trim('/').split('/')
        if (parts.size < 5 || parts[2] != "resolve") throw IOException("Unrecognised model URL.")
        val owner = parts[0]
        val repo = parts[1]
        val path = parts.drop(4).joinToString("/")
        val api = URL("https://${u.host}/api/models/$owner/$repo?blobs=true")
        val c = api.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "RomanUrduKeyboard/0.2")
            if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
            if (c.responseCode != 200) throw IOException("Hugging Face answered HTTP ${c.responseCode}.")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val siblings = JSONObject(body).getJSONArray("siblings")
            for (i in 0 until siblings.length()) {
                val o = siblings.getJSONObject(i)
                if (o.optString("rfilename") == path) {
                    val lfs = o.optJSONObject("lfs") ?: throw IOException("No checksum listed for that file.")
                    return lfs.getString("sha256") to o.optLong("size", lfs.optLong("size", -1L))
                }
            }
            throw IOException("File not listed in the repo.")
        } finally {
            c.disconnect()
        }
    }

    fun verify(
        file: File,
        url: String,
        token: String,
        onProgress: (done: Long, total: Long) -> Unit,
        cancelled: () -> Boolean,
    ): Verdict {
        val (sha, size) = try {
            expectedFromHub(url, token)
        } catch (t: Throwable) {
            return Verdict.Unknown("Could not get the checksum from Hugging Face (${t.message}).")
        }
        if (size > 0 && file.length() != size) {
            return Verdict.Damaged("Size is ${file.length()} bytes, Hugging Face says $size.")
        }
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 20)
            var done = 0L
            var last = 0L
            while (true) {
                if (cancelled()) throw CancelledException()
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                val now = System.nanoTime()
                if (now - last > 250_000_000L) { onProgress(done, file.length()); last = now }
            }
            onProgress(done, file.length())
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        return if (actual.equals(sha, ignoreCase = true)) Verdict.Ok
        else Verdict.Damaged("Checksum ${actual.take(10)}\u2026 does not match Hugging Face's ${sha.take(10)}\u2026.")
    }
}
