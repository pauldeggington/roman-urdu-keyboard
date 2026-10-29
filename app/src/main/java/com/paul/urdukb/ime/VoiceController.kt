package com.paul.urdukb.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.paul.urdukb.MicPermissionActivity
import com.paul.urdukb.R

/**
 * The mic button. Listens inside the keyboard (the keys stay visible) through [VoiceInput]; asks for the microphone the
 * first time; and where the phone has no speech recogniser, hands over to the phone's own voice typing keyboard.
 */
internal class VoiceController(
    private val service: InputMethodService,
    private val handler: Handler,
    private val onListening: (Boolean) -> Unit,
    private val beforeStart: () -> Unit,
) {
    private var voice: VoiceInput? = null

    val listening: Boolean get() = voice?.listening == true

    fun stop() { voice?.stop() }

    /** The mic button: starts listening, or taps again to stop. */
    fun toggle() {
        val v = voice ?: VoiceInput(
            service, { service.currentInputConnection },
            { on -> handler.post { onListening(on) } },
            { msg -> handler.post { problem(msg) } },
        ).also { voice = it }
        if (v.listening) { v.stop(); return }
        if (!VoiceInput.available(service)) { handOverToPhoneVoiceTyping(); return }
        if (!VoiceInput.hasPermission(service)) { askMicPermission(); return }
        beforeStart()
        v.start()
    }

    private fun askMicPermission() {
        MicPermissionActivity.onResult = { ok ->
            MicPermissionActivity.onResult = null
            if (ok) handler.post { voice?.start() }
            else handler.post { Toast.makeText(service, service.getString(R.string.mic_off), Toast.LENGTH_LONG).show() }
        }
        service.startActivity(Intent(service, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun problem(msg: String) {
        if (msg == "NEEDS_PERMISSION") { askMicPermission(); return }
        Toast.makeText(service, msg, Toast.LENGTH_LONG).show()
    }

    /**
     * Fallback voice typing: hands over to the phone's voice typing keyboard (Google's), which switches back here when
     * you stop talking.
     */
    private fun handOverToPhoneVoiceTyping() {
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return
        for (info in imm.enabledInputMethodList) {
            if (info.packageName == service.packageName) continue
            for (st in imm.getEnabledInputMethodSubtypeList(info, true)) {
                if (st.mode.equals("voice", ignoreCase = true)) {
                    try {
                        service.switchInputMethod(info.id, st)
                        return
                    } catch (_: Exception) {
                    }
                }
            }
        }
        Toast.makeText(service, service.getString(R.string.voice_not_on), Toast.LENGTH_LONG).show()
    }
}
