package com.paul.urdukb

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from GitHub Releases. The app only asks the GitHub API which version is newest; the download
 * itself happens in the browser, and the person taps the downloaded file to install it. The app never
 * installs anything itself, so it needs no install permission. Releases are signed with the same key,
 * so they install over this one and keep settings and the translator.
 */
object Updater {
    const val REPO = "pauldeggington/roman-urdu-keyboard"
    private const val ASSET = "roman-urdu-keyboard.apk"
    private val VERSIONED_ASSET = Regex("RomanUrduKeyboard-[0-9][0-9.]*\\.apk")
    private const val PREF_DL = "update_dm_id"

    data class Release(val tag: String, val versionCode: Long, val name: String, val apkUrl: String, val notes: String, val page: String)

    fun installedCode(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
    fun installedName(ctx: Context): String = display(ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?")

    /** "2.1.121" is shown as "2.1 (build 121)"; a test build keeps its "-test" ending. */
    fun display(name: String): String {
        val m = Regex("^(\\d+\\.\\d+)\\.(\\d+)(-.*)?$").matchEntire(name) ?: return name
        return "${m.groupValues[1]} (build ${m.groupValues[2]})${m.groupValues[3]}"
    }

    /** CI tags releases v<major>.<minor>.<build> and sets versionCode 4300 + <build>. */
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
            // releases carry RomanUrduKeyboard-<version>.apk (the -standard copy is the unshrunk fallback, never picked here);
            // older releases used the plain name
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.getString("name")
                if (VERSIONED_ASSET.matches(n) || (url.isEmpty() && n == ASSET)) url = a.getString("browser_download_url")
            }
            if (url.isEmpty()) throw IllegalStateException("The latest release has no APK.")
            return Release(tag, codeFromTag(tag), o.optString("name", tag), url, o.optString("body", ""), o.optString("html_url", ""))
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(ctx: Context, r: Release) = r.versionCode > installedCode(ctx)
}
