package com.paul.urdukb

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.settings.findIndexOfDefaultSetting
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Gboard-style tools panel: opens in place of the keys when the grid button or the gear is tapped.
 * Page 1 mirrors Gboard (Translate, Emoji, Theme, Text editing, Resize, One-handed); page 2 has the
 * Roman Urdu controls and shortcuts. Colours come from the keyboard's current theme.
 */
class RomanUrduPanel(context: Context, private val host: Host) : FrameLayout(context) {

    interface Host {
        /** Run a keyboard key code (as if the key was pressed). */
        fun send(code: Int)
        /** Close the panel (and the toolbar row). */
        fun close()
        fun openTheme()
        fun openApp()
        /** Rebuild the keyboard so a new height takes effect. */
        fun applyResize()
    }

    private class Item(val icon: Int, val label: () -> String, val chevron: Boolean, val action: () -> Unit)

    private var page = 0
    private var resizing = false
    private var downX = 0f
    private var downY = 0f

    private var bg = Color.BLACK
    private var fg = Color.WHITE
    private var pill = Color.DKGRAY
    private var pillPressed = Color.GRAY
    private var accent = Color.LTGRAY
    private var onAccent = Color.BLACK
    private var muted = Color.GRAY

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    /** Rebuild with the current colours. Called every time the panel is shown. */
    fun refresh() {
        val colors = Settings.getValues().mColors
        bg = colors.get(ColorType.MAIN_BACKGROUND) or -0x1000000
        fg = colors.get(ColorType.KEY_TEXT) or -0x1000000
        accent = colors.get(ColorType.ACTION_KEY_BACKGROUND) or -0x1000000
        val dark = ColorUtils.calculateLuminance(bg) < 0.5
        pill = ColorUtils.blendARGB(bg, fg, if (dark) 0.14f else 0.10f)
        pillPressed = ColorUtils.blendARGB(bg, fg, if (dark) 0.26f else 0.20f)
        muted = ColorUtils.setAlphaComponent(fg, 150)
        onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
        page = 0
        resizing = false
        build()
    }

    private fun build() {
        removeAllViews()
        setBackgroundColor(bg)
        if (resizing) buildResize() else buildPages()
    }

    // ------------------------------------------------------------------ pages

    private fun pageItems(index: Int): List<Item> {
        val ctx = context
        return if (index == 0) listOf(
            Item(R.drawable.ic_rurdu_translate_base, { "Translate" }, false) { host.close(); host.send(KeyCode.ROMAN_URDU_TRANSLATE) },
            Item(R.drawable.sym_keyboard_smiley_rounded, { "Emoji" }, true) { host.close(); host.send(KeyCode.EMOJI) },
            Item(R.drawable.ic_rurdu_palette, { "Theme" }, true) { host.close(); host.openTheme() },
            Item(R.drawable.ic_rurdu_text_edit, { "Text editing" }, true) { host.close(); host.send(KeyCode.DPAD) },
            Item(R.drawable.ic_rurdu_resize, { "Resize" }, false) { resizing = true; build() },
            Item(R.drawable.ic_rurdu_onehanded, { "One-handed" }, false) { host.close(); host.send(KeyCode.TOGGLE_ONE_HANDED_MODE) },
        ) else listOf(
            Item(
                R.drawable.ic_rurdu_dir_auto,
                {
                    when (RomanUrdu.direction(ctx)) {
                        RomanUrdu.DIR_TO_RU -> "To Roman Urdu"
                        RomanUrdu.DIR_TO_EN -> "To English"
                        else -> "Auto direction"
                    }
                },
                false,
            ) { host.send(KeyCode.ROMAN_URDU_DIRECTION); build() },
            Item(
                R.drawable.ic_rurdu_casual,
                { if (RomanUrdu.isFormal(ctx)) "Formal · aap" else "Casual · tum" },
                false,
            ) { host.send(KeyCode.ROMAN_URDU_TONE); build() },
            Item(R.drawable.sym_keyboard_clipboard_rounded, { "Clipboard" }, true) { host.close(); host.send(KeyCode.CLIPBOARD) },
            Item(R.drawable.sym_keyboard_settings_rounded, { "All settings" }, true) { host.close(); host.send(KeyCode.SETTINGS) },
            Item(R.drawable.ic_launcher_foreground, { "Roman Urdu app" }, true) { host.close(); host.openApp() },
            Item(R.drawable.sym_keyboard_incognito_lxx, { "Incognito" }, false) { host.send(KeyCode.TOGGLE_INCOGNITO_MODE) },
        )
    }

    private fun buildPages() {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(22f), dpi(24f), 0)
        }
        val items = pageItems(page)
        for (r in 0 until 3) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(55.6f)).apply {
                    topMargin = if (r == 0) 0 else dpi(8.8f)
                }
            }
            for (c in 0 until 2) {
                val item = items[r * 2 + c]
                row.addView(pillView(item), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (c == 0) marginEnd = dpi(4.4f) else marginStart = dpi(4.4f)
                })
            }
            content.addView(row)
        }
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // page dots (centre) and the edit pill (right), like Gboard
        val dots = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0..1) {
            val size = dpi(if (i == page) 7f else 6f)
            dots.addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == page) fg else muted)
                }
                setOnClickListener { page = i; build() }
            }, LinearLayout.LayoutParams(size, size).apply {
                marginStart = dpi(5f)
                marginEnd = dpi(5f)
                gravity = Gravity.CENTER_VERTICAL
            })
        }
        addView(dots, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpi(24f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpi(14f)
        })

        val edit = ImageView(context).apply {
            setImageResource(R.drawable.ic_rurdu_edit)
            setColorFilter(fg)
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(16f)
                setStroke(dpi(1.5f), muted)
            }
            contentDescription = "Edit"
            setOnClickListener { host.close(); host.send(KeyCode.SETTINGS) }
        }
        addView(edit, LayoutParams(dpi(71f), dpi(31f), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dpi(24f)
            bottomMargin = dpi(10f)
        })
    }

    private fun pillView(item: Item): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(18f), 0, dpi(14f), 0)
            background = pillBackground()
            isClickable = true
            setOnClickListener { item.action() }
        }
        row.addView(ImageView(context).apply {
            setImageResource(item.icon)
            if (item.icon != R.drawable.ic_launcher_foreground) setColorFilter(fg)
        }, LinearLayout.LayoutParams(dpi(24f), dpi(24f)))
        row.addView(TextView(context).apply {
            this.text = item.label()
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dpi(14f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (item.chevron) {
            row.addView(TextView(context).apply {
                this.text = "›"
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            })
        }
        return row
    }

    private fun pillBackground(): android.graphics.drawable.Drawable {
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(28f)
            setColor(color)
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pillPressed))
            addState(intArrayOf(), shape(pill))
        }
    }

    // ------------------------------------------------------------------ resize

    private fun heightScaleKey(): Pair<String, Float> {
        val dm = resources.displayMetrics
        val landscape = dm.widthPixels > dm.heightPixels
        val folded = FoldableUtils.isFolded
        val index = findIndexOfDefaultSetting(landscape, folded)
        val key = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, index, 2)
        return key to Settings.readHeightScale(context.prefs(), landscape, folded)
    }

    private fun buildResize() {
        val (key, current) = heightScaleKey()
        var pct = (current * 100f).roundToInt()

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(24f), dpi(22f), dpi(24f), 0)
        }
        col.addView(TextView(context).apply {
            this.text = "Keyboard height"
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(TextView(context).apply {
            this.text = "100% matches Gboard's height."
            setTextColor(muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dpi(4f), 0, dpi(14f))
        })

        val value = TextView(context).apply {
            this.text = "$pct%"
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        }
        fun step(delta: Int) {
            pct = (pct + delta).coerceIn(70, 140)
            value.text = "$pct%"
            context.prefs().edit { putFloat(key, pct / 100f) }
        }
        fun roundBtn(label: String, onClick: () -> Unit) = TextView(context).apply {
            this.text = label
            gravity = Gravity.CENTER
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            background = pillBackground()
            setOnClickListener { onClick() }
        }

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(roundBtn("−") { step(-5) }, LinearLayout.LayoutParams(dpi(72f), dpi(52f)))
        row.addView(value, LinearLayout.LayoutParams(dpi(110f), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(roundBtn("+") { step(5) }, LinearLayout.LayoutParams(dpi(72f), dpi(52f)))
        col.addView(row)

        val done = TextView(context).apply {
            this.text = "Done"
            gravity = Gravity.CENTER
            setTextColor(onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable().apply { cornerRadius = dp(26f); setColor(accent) }
            setOnClickListener { resizing = false; host.close(); host.applyResize() }
        }
        col.addView(done, LinearLayout.LayoutParams(dpi(160f), dpi(48f)).apply { topMargin = dpi(20f) })
        addView(col, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ------------------------------------------------------------------ swipe between pages

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!resizing && abs(dx) > dp(70f) && abs(dx) > abs(dy) * 1.5f) {
                    val target = if (dx < 0) 1 else 0
                    if (target != page) {
                        page = target
                        post { build() }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
