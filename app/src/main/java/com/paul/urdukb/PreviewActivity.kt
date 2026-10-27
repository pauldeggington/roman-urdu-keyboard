package com.paul.urdukb

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

/** A plain text box that opens the keyboard. Used to look at the keyboard (screenshots in CI). */
class PreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("markdone", false)) {
            SetupActivity.markDone(this)
            finish()
            return
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val box = EditText(this).apply {
            hint = "Type here"
            textSize = 20f
            gravity = Gravity.TOP
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = when (intent.getStringExtra("type")) {
                "number" -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            intent.getStringExtra("text")?.let { setText(it) }
        }
        val root = LinearLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setPadding(48, 160, 48, 48)
            addView(box, LinearLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        box.requestFocus()
    }
}
