package com.paul.urdukb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.paul.urdukb.ui.RomanUrduTheme
import com.paul.urdukb.ui.SetupModel
import com.paul.urdukb.ui.SetupScreen

/**
 * First-run setup: one task per screen, a progress bar, buttons that jump straight to the right
 * Android setting, and steps that tick themselves off when the person comes back.
 *
 *   Welcome -> Download the translator -> Turn on the keyboard -> Switch to it
 *   -> Make it yours -> Practice -> Done
 *
 * The model downloads in the background (Android's download manager), so the keyboard steps can be
 * done while it runs. What each screen shows is in [SetupScreen]; what it does is in [SetupModel].
 */
class SetupActivity : ComponentActivity() {

    enum class Step { WELCOME, MODEL, ENABLE, SWITCH, PERSONAL, PRACTICE, DONE }

    private lateinit var model: SetupModel

    // the system file picker, for a model file that is already on the phone
    private val pickModelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val pal = Palette(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !pal.dark
            isAppearanceLightNavigationBars = !pal.dark
        }
        model = SetupModel(this) { openApp ->
            if (openApp) startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        setContent {
            RomanUrduTheme {
                SetupScreen(model, pickModelFile = { pickModelFile.launch(arrayOf("*/*")) }, recreateScreen = { recreate() }, closeGuide = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.start()
    }

    override fun onPause() {
        super.onPause()
        model.stop()
    }

    // the keyboard picker is a dialog: when it closes, focus comes back here
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::model.isInitialized) model.tick()
    }

    companion object {
        internal const val PREF_STEP = "setup_step"
        internal const val PREF_DONE = "setup_done"
        internal const val PREF_WIFI_ONLY = "setup_wifi_only"

        fun isDone(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(PREF_DONE, false)

        fun markDone(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_DONE, true).apply()

        /** The keyboard changed (an update replaced it), so ask the person to turn the new one on. */
        fun resumeAtEnable(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_DONE, false).putString(PREF_STEP, Step.ENABLE.name).apply()

        fun reset(ctx: Context) =
            Prefs.store(ctx).getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_DONE, false).remove(PREF_STEP).apply()
    }
}
