package com.paul.urdukb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.paul.urdukb.ui.RomanUrduTheme
import com.paul.urdukb.ui.VoiceModel
import com.paul.urdukb.ui.VoiceScreen
import java.io.File

/**
 * Two ways in. An Urdu voice note shared to this app (from WhatsApp's Share menu) is read out as Urdu text and English.
 * Opened from the app with "speak", it records English and makes an Urdu voice message. What it shows is in [VoiceScreen];
 * what it does is in [VoiceModel].
 */
class VoiceActivity : ComponentActivity() {

    private val model: VoiceModel by viewModels()

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.toggleRecording()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = sharedAudio(intent)
        when {
            shared != null -> model.openSharedAudio(shared)
            intent?.getStringExtra(EXTRA_MODE) == MODE_SPEAK -> model.prepareSpeak()
            else -> { finish(); return }
        }
        setContent {
            RomanUrduTheme {
                VoiceScreen(
                    model,
                    onRecord = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.toggleRecording()
                        else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onShareAudio = { shareAudio(it) },
                    onVoiceSettings = { openVoiceSettings() },
                    onClose = { finish() },
                )
            }
        }
    }

    /** The audio another app shared with us, or null. */
    private fun sharedAudio(i: Intent?): Uri? {
        if (i?.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

    private fun shareAudio(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, null))
    }

    private fun openVoiceSettings() {
        val tts = Intent("com.android.settings.TTS_SETTINGS")
        try { startActivity(tts) } catch (_: Throwable) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
        }
    }

    companion object {
        const val EXTRA_MODE = "voice_mode"
        const val MODE_SPEAK = "speak"
    }
}
