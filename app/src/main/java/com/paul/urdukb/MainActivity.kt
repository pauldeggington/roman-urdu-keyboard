package com.paul.urdukb

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.paul.urdukb.ui.MainModel
import com.paul.urdukb.ui.MainScreen
import com.paul.urdukb.ui.RomanUrduTheme
import com.paul.urdukb.ui.Section
import com.paul.urdukb.ui.Tab
import com.paul.urdukb.ui.VoiceActions
import com.paul.urdukb.ui.VoiceModel
import java.io.File

/**
 * The app screen: update, setup checklist, model, Try it, style, colour, speed, keyboard settings.
 * What it shows is in [MainScreen]; what it does is in [MainModel]. This class only starts them.
 */
class MainActivity : ComponentActivity() {

    // Lives in a ViewModel, so a turned screen or a colour change keeps the download, the Try it text and the style editor.
    private val model: MainModel by viewModels()
    private val voice: VoiceModel by viewModels()
    private var ready = false

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.toggleRecording()
    }

    // an Urdu voice note already saved on the phone
    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) voice.openSharedAudio(uri, again = true)
    }

    // the system file picker, to use a model file that is already on the phone
    private val pickModelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // the app is always dark, so the status bar icons are always light
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val kbEnabled = getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.any { it.packageName == packageName } == true
        // an update from the old keyboard replaces it with a new one that has to be turned on again
        if (SetupActivity.isDone(this) && !kbEnabled) SetupActivity.resumeAtEnable(this)
        if (!SetupActivity.isDone(this)) {
            if (LlmEngine.modelExists(this) && kbEnabled) SetupActivity.markDone(this) // set up before the guide existed
            else {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                return
            }
        }
        ready = true
        setContent {
            val keepAwake = model.keepScreenOn
            LaunchedEffect(keepAwake) {
                if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val actions = VoiceActions(
                onRecord = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.toggleRecording()
                    else askMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                onShareAudio = { shareAudio(it) },
                onVoiceSettings = { openVoiceSettings() },
                onPickAudio = { pickAudio.launch(arrayOf("audio/*", "video/*", "application/ogg")) },
            )
            RomanUrduTheme {
                MainScreen(model, voice, actions, pickModelFile = { pickModelFile.launch(arrayOf("*/*")) }, recreateScreen = { recreate() })
            }
        }
        scrollToRequestedSection()
    }

    override fun onResume() {
        super.onResume()
        if (ready) model.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scrollToRequestedSection()
    }

    private fun scrollToRequestedSection() {
        if (!ready) return
        when (intent?.getStringExtra(EXTRA_SECTION)) {
            "colour" -> model.scrollRequest = Section.COLOUR
            "giphy" -> model.scrollRequest = Section.GIPHY
            "voice" -> { model.tab = Tab.VOICE; voice.prepareSpeak() }
            "learn" -> model.tab = Tab.LEARN
        }
        intent?.removeExtra(EXTRA_SECTION)
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
        const val EXTRA_SECTION = "section"
    }
}
