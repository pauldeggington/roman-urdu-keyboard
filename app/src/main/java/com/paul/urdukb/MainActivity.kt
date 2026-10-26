package com.paul.urdukb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/** Setup screen: import the model file, enable the keyboard, and test it. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Roman Urdu Keyboard"
            textSize = 22f
        })

        status = TextView(this).apply { setPadding(0, pad, 0, pad) }
        root.addView(status)

        root.addView(button("1. Choose model file (.task)") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                },
                REQ_MODEL
            )
        })
        root.addView(button("2. Enable keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("3. Switch to this keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(EditText(this).apply {
            hint = "Test here"
            setPadding(0, pad, 0, pad)
        })

        setContentView(root)
        refreshStatus()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun refreshStatus(extra: String? = null) {
        val f = LlmEngine.modelFile(this)
        val base = if (LlmEngine.modelExists(this))
            "Model loaded: ${f.length() / (1024 * 1024)} MB"
        else
            "No model yet. Pick the Gemma .task file you downloaded."
        status.text = if (extra != null) "$base\n$extra" else base
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_MODEL && resultCode == RESULT_OK && uri != null) importModel(uri)
    }

    private fun importModel(uri: Uri) {
        status.text = "Copying model… this takes a minute for a ~1 GB file."
        io.execute {
            val result = try {
                val tmp = File(filesDir, "model.task.tmp")
                contentResolver.openInputStream(uri)!!.use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
                }
                val dest = LlmEngine.modelFile(this)
                dest.delete()
                tmp.renameTo(dest)
                LlmEngine.reset()
                null
            } catch (e: Throwable) {
                "Import failed: ${e.message}"
            }
            runOnUiThread { refreshStatus(result) }
        }
    }

    companion object {
        private const val REQ_MODEL = 1
    }
}
