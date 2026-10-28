package com.paul.urdukb

import android.app.Application
import android.os.Process
import android.os.StrictMode
import com.paul.urdukb.ime.EmojiData
import com.paul.urdukb.ime.GlideDict

/**
 * Starts the app, then loads the data the keyboard needs (spelling variants, emoji list, glide word list) on a
 * background thread, so none of it is read on the thread that draws the keys.
 */
class UrduApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.PREVIEW) {
            // emulator test builds: log anything that reads files or the network on the main thread
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedRegistrationObjects().penaltyLog().build())
        }
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            VariantMap.available(this)
            EmojiData.load(this)
            GlideDict.load(this)
        }, "warm-data").start()
    }
}
