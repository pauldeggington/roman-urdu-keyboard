package com.paul.urdukb

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.paul.urdukb.ui.MainModel
import com.paul.urdukb.ui.MainScreen
import com.paul.urdukb.ui.RomanUrduTheme
import com.paul.urdukb.ui.Section

/**
 * The app screen: update, setup checklist, model, Try it, style, colour, speed, keyboard settings.
 * What it shows is in [MainScreen]; what it does is in [MainModel]. This class only starts them.
 */
class MainActivity : ComponentActivity() {

    private var model: MainModel? = null

    // the system file picker, to use a model file that is already on the phone
    private val pickModelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model?.importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val pal = Palette(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !pal.dark
            isAppearanceLightNavigationBars = !pal.dark
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
        val m = MainModel(this)
        model = m
        setContent {
            RomanUrduTheme {
                MainScreen(m, pickModelFile = { pickModelFile.launch(arrayOf("*/*")) }, recreateScreen = { recreate() })
            }
        }
        scrollToRequestedSection()
    }

    override fun onResume() {
        super.onResume()
        model?.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scrollToRequestedSection()
    }

    private fun scrollToRequestedSection() {
        when (intent?.getStringExtra(EXTRA_SECTION)) {
            "colour" -> model?.scrollRequest = Section.COLOUR
            "giphy" -> model?.scrollRequest = Section.GIPHY
        }
        intent?.removeExtra(EXTRA_SECTION)
    }

    companion object {
        const val EXTRA_SECTION = "section"
    }
}
