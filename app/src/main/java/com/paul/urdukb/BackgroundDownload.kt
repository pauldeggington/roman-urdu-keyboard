package com.paul.urdukb

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import java.io.File

/**
 * Model download through Android's own download manager, so it keeps going (with a
 * notification) while people are in Settings turning the keyboard on, or if they close the app.
 * The file lands next to the model as "model.litertlm.download" and is renamed when complete,
 * so the keyboard never sees a half-written model.
 */
object BackgroundDownload {
    private const val PREF_ID = "dm_id"

    data class State(
        val status: Int,        // DownloadManager.STATUS_* or -1 when nothing is queued
        val done: Long,
        val total: Long,
        val reason: Int,
    ) {
        val running get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
        val paused get() = status == DownloadManager.STATUS_PAUSED
        val active get() = running || paused
    }

    private fun sp(ctx: Context) = Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun dm(ctx: Context) = ctx.getSystemService(DownloadManager::class.java)
    private fun id(ctx: Context) = sp(ctx).getLong(PREF_ID, -1L)

    fun tempFile(ctx: Context) = File(LlmEngine.modelFile(ctx).path + ".download")

    fun isMetered(ctx: Context): Boolean =
        ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

    fun start(ctx: Context, allowMobileData: Boolean) {
        cancel(ctx)
        val tmp = tempFile(ctx)
        tmp.parentFile?.mkdirs()
        tmp.delete()
        val req = DownloadManager.Request(Uri.parse(Prefs.url(ctx)))
            .setTitle("Roman Urdu Keyboard")
            .setDescription("Downloading the translation model (about 2.6 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(allowMobileData)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(tmp))
            .addRequestHeader("User-Agent", "RomanUrduKeyboard/1.0")
        val token = Prefs.token(ctx)
        if (token.isNotBlank()) req.addRequestHeader("Authorization", "Bearer $token")
        Prefs.setLastError(ctx, "")
        sp(ctx).edit().putLong(PREF_ID, dm(ctx).enqueue(req)).apply()
    }

    fun cancel(ctx: Context) {
        val id = id(ctx)
        if (id >= 0) runCatching { dm(ctx).remove(id) }
        sp(ctx).edit().remove(PREF_ID).apply()
        tempFile(ctx).delete()
    }

    fun state(ctx: Context): State {
        val id = id(ctx)
        if (id < 0) return State(-1, 0, 0, 0)
        dm(ctx).query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) {
                fun col(name: String) = c.getColumnIndex(name)
                return State(
                    c.getInt(col(DownloadManager.COLUMN_STATUS)),
                    c.getLong(col(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(col(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    c.getInt(col(DownloadManager.COLUMN_REASON)),
                )
            }
        }
        // the system forgot it (user cleared downloads): treat as nothing queued
        sp(ctx).edit().remove(PREF_ID).apply()
        return State(-1, 0, 0, 0)
    }

    /** Plain-words reason for a pause or failure. */
    fun reasonText(s: State): String = when (s.status) {
        DownloadManager.STATUS_PAUSED -> when (s.reason) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a connection"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection dropped, trying again soon"
            else -> "Paused"
        }
        DownloadManager.STATUS_FAILED -> when (s.reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space (needs about 3 GB)"
            DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "The connection broke. Try again"
            in 400..599 -> "The server answered ${s.reason}. Try again later"
            else -> "The download failed. Try again"
        }
        else -> ""
    }

    /**
     * When the system says it is done, moves the file into place. Returns the model file if this call
     * installed it (the caller then checks it is intact), or null if there was nothing to finish.
     */
    fun finishIfDone(ctx: Context): File? {
        val s = state(ctx)
        if (s.status != DownloadManager.STATUS_SUCCESSFUL) return null
        val tmp = tempFile(ctx)
        val dest = LlmEngine.modelFile(ctx)
        sp(ctx).edit().remove(PREF_ID).apply()
        if (!tmp.exists() || tmp.length() == 0L) {
            Prefs.setLastError(ctx, "The download finished but the file was missing. Try again.")
            return null
        }
        LlmEngine.reset()
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            Prefs.setLastError(ctx, "Could not move the downloaded model into place.")
            return null
        }
        Prefs.setModelSize(ctx, dest.length())
        Prefs.setVerifiedStamp(ctx, "")
        Prefs.setDamagedStamp(ctx, "")
        return dest
    }
}
