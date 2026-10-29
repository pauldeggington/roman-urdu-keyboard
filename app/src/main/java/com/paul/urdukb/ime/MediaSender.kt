package com.paul.urdukb.ime

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Handler
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.paul.urdukb.R

/** Sends a GIF or sticker to the app being typed in: as a picture where the app accepts one, otherwise as a link. */
internal class MediaSender(private val service: InputMethodService, private val handler: Handler) {

    fun send(item: GifItem) {
        val info = service.currentInputEditorInfo
        val ic = service.currentInputConnection
        if (info == null || ic == null) return
        val accepts = EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes("image/gif", it) }
        if (!accepts) {
            ic.commitText(item.pageUrl + " ", 1)
            Toast.makeText(service, service.getString(R.string.gif_link_typed), Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val file = MediaCache.download(service, item)
            handler.post {
                val ic2 = service.currentInputConnection
                val info2 = service.currentInputEditorInfo
                var sent = false
                if (file != null && ic2 != null && info2 != null) {
                    val uri = FileProvider.getUriForFile(service, "${service.packageName}.fileprovider", file)
                    val content = InputContentInfoCompat(uri, ClipDescription(item.title.ifEmpty { "GIF" }, arrayOf("image/gif")), Uri.parse(item.pageUrl))
                    sent = InputConnectionCompat.commitContent(ic2, info2, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                }
                if (!sent) {
                    ic2?.commitText(item.pageUrl + " ", 1)
                    Toast.makeText(service, service.getString(R.string.gif_send_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
