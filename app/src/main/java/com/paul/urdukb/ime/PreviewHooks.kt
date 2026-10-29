package com.paul.urdukb.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/** Only in emulator preview builds: lets CI switch screens and read key positions to take screenshots. */
internal fun UrduImeService.registerPreviewHooks() {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            handler.post {
                if (!viewReady) return@post
                when (intent.getStringExtra("do")) {
                    "letters" -> { showEmoji(false); keyboard.page = Page.LETTERS }
                    "symbols1" -> { showEmoji(false); keyboard.page = Page.SYMBOLS1 }
                    "symbols2" -> { showEmoji(false); keyboard.page = Page.SYMBOLS2 }
                    "emoji" -> { showEmoji(true); emoji.showMode("emoji") }
                    "emoticons" -> { showEmoji(true); emoji.showMode("emoticon") }
                    "clipboard" -> { ClipStore.seedForPreview(this@registerPreviewHooks); showClipboard(true) }
                    "clipboard_off" -> { ClipStore.setEnabled(this@registerPreviewHooks, false); showClipboard(true) }
                    "gif" -> { showEmoji(true); emoji.showMode("gif") }
                    "stickers" -> { showEmoji(true); emoji.showMode("sticker") }
                    "search_emoji" -> { showEmoji(true); emoji.showMode("emoji"); emoji.openSearchFromHook(); emoji.searchType(intent.getStringExtra("q") ?: "heart") }
                    "search_gif" -> { showEmoji(true); emoji.showMode("gif"); emoji.openSearchFromHook(); emoji.searchType("thanks") }
                    "tools" -> toolbar.toolsOpen = true
                    "notools" -> toolbar.toolsOpen = false
                    "shift" -> keyboard.shift = KeyboardView.SHIFT_ON
                    "space" -> { typeSpace(); assist.schedule() }
                    "back" -> deleteOne()
                    "paste_offer" -> {
                        ClipStore.seedForPreview(this@registerPreviewHooks)
                        assist.offerPaste("Meeting moved to Thursday 3 pm")
                        showEmoji(false); keyboard.page = Page.LETTERS
                    }
                    "gif_recent" -> {
                        for ((i, c) in listOf("e57373", "4fc3f7", "aed581").withIndex())
                            RecentMedia.add(this@registerPreviewHooks, GifItem("pv$i", "Sample", "preview:$c", "preview:$c", "https://giphy.com", 200, 150 + 40 * i, false))
                        showEmoji(true); emoji.showMode("gif")
                    }
                    "mic_on" -> toolbar.listening = true
                    "mic_off" -> toolbar.listening = false
                    "dump" -> {
                        keyboard.keyCenters().forEach { Log.i("PREVIEW", "KEY ${it.first} ${it.second.toInt()} ${it.third.toInt()}") }
                        val loc = IntArray(2)
                        toolbar.getLocationOnScreen(loc)
                        Log.i("PREVIEW", "TOOLBAR ${loc[0]} ${loc[1]} ${toolbar.width} ${toolbar.height}")
                    }
                }
            }
        }
    }
    val filter = IntentFilter("com.paul.urdukb.PREVIEW")
    androidx.core.content.ContextCompat.registerReceiver(this, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
}
