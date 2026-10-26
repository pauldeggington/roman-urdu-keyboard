package com.paul.urdukb

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from GitHub Releases: asks the GitHub API for the latest release, downloads its APK with the
 * system download manager, then hands it to Android's package installer, which shows the usual
 * "Update this app?" prompt. Releases are signed with the same key, so they install over this one.
 */
object Updater {
    const val REPO = "pauldeggington/roman-urdu-keyboard"
    private const val ASSET = "roman-urdu-keyboard.apk"
    private const val PREF_DL = "update_dm_id"

    data class Release(val tag: String, val versionCode: Long, val name: String, val apkUrl: String, val notes: String, val page: String)

    fun installedCode(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
    fun installedName(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** CI tags releases v1.0.<run> and sets versionCode 4300 + <run>. */
    fun codeFromTag(tag: String): Long = tag.substringAfterLast('.').toLongOrNull()?.let { 4300 + it } ?: -1

    /** Blocking; call off the main thread. */
    fun latest(): Release {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "RomanUrduKeyboard")
            if (c.responseCode == 404) throw IllegalStateException("No releases published yet.")
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered HTTP ${c.responseCode}.")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            val assets = o.getJSONArray("assets")
            var url = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == ASSET) url = a.getString("browser_download_url")
            }
            if (url.isEmpty()) throw IllegalStateException("The latest release has no APK.")
            return Release(tag, codeFromTag(tag), o.optString("name", tag), url, o.optString("body", ""), o.optString("html_url", ""))
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(ctx: Context, r: Release) = r.versionCode > installedCode(ctx)

    // ---------------------------------------------------------------- download

    private fun sp(ctx: Context) = Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun dm(ctx: Context) = ctx.getSystemService(DownloadManager::class.java)
    fun apkFile(ctx: Context) = File(Prefs.store(ctx).getExternalFilesDir(null) ?: Prefs.store(ctx).filesDir, "update.apk")

    fun startDownload(ctx: Context, r: Release) {
        cancelDownload(ctx)
        val f = apkFile(ctx)
        f.delete()
        val req = DownloadManager.Request(Uri.parse(r.apkUrl))
            .setTitle("Roman Urdu Keyboard ${r.tag}")
            .setDescription("Downloading the update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(f))
        sp(ctx).edit().putLong(PREF_DL, dm(ctx).enqueue(req)).apply()
    }

    fun cancelDownload(ctx: Context) {
        val id = sp(ctx).getLong(PREF_DL, -1)
        if (id >= 0) runCatching { dm(ctx).remove(id) }
        sp(ctx).edit().remove(PREF_DL).apply()
    }

    /** (status, done, total) of the update download, status -1 when none. */
    fun downloadState(ctx: Context): Triple<Int, Long, Long> {
        val id = sp(ctx).getLong(PREF_DL, -1)
        if (id < 0) return Triple(-1, 0, 0)
        dm(ctx).query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) return Triple(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
        sp(ctx).edit().remove(PREF_DL).apply()
        return Triple(-1, 0, 0)
    }

    fun clearDownload(ctx: Context) = sp(ctx).edit().remove(PREF_DL).apply()

    // ---------------------------------------------------------------- install

    /** Android asks once per app before it may install updates. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    /** Hands the downloaded APK to the package installer. Blocking; call off the main thread. */
    fun install(ctx: Context) {
        val f = apkFile(ctx)
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(f.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            f.inputStream().use { input ->
                session.openWrite("base.apk", 0, f.length()).use { out ->
                    input.copyTo(out, 1 shl 20)
                    session.fsync(out)
                }
            }
            val pi = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstallResult::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    /** Receives the installer's answer; shows Android's confirmation screen when it asks for one. */
    class InstallResult : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts on the new version
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                    Prefs.setLastError(context, "Update failed: $msg")
                    android.widget.Toast.makeText(context, "Update failed: $msg", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
