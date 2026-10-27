package com.paul.urdukb.ime

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.paul.urdukb.R

/**
 * Gboard's clipboard screen: cards for what you copied recently, tap one to paste it, long-press to pin or delete.
 * History is off until the person turns it on.
 */
class ClipboardPanel(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onClip(text: String)
        fun onBack()
        fun onDeleteDown()
        fun onDeleteUp()
        fun onFeedback()
    }

    var listener: Listener? = null
    var colors: KbColors = KbColors.of(context)
        set(v) { field = v; applyColors() }

    private val d = resources.displayMetrics.density
    private val font = ResourcesCompat.getFont(context, R.font.google_sans_text) ?: Typeface.DEFAULT
    private var clips: List<ClipStore.Clip> = emptyList()

    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val topBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val backBtn = ImageView(context)
    private val title = TextView(context)
    private val clearBtn = TextView(context)
    private val hint = TextView(context)
    private val list = RecyclerView(context)
    private val messageBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = GONE }
    private val messageText = TextView(context)
    private val primaryBtn = TextView(context)
    private val secondaryBtn = TextView(context)
    private val bottomBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val abc = TextView(context)
    private val deleteBtn = ImageView(context)
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; visibility = GONE }
    private val scrim = View(context).apply { visibility = GONE }

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = clips.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(context).apply {
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                maxLines = 7
                ellipsize = TextUtils.TruncateAt.END
                minHeight = (64 * d).toInt()
                gravity = Gravity.TOP or Gravity.START
                compoundDrawablePadding = (6 * d).toInt()
                setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            }
            return object : RecyclerView.ViewHolder(tv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val tv = holder.itemView as TextView
            val clip = clips[position]
            val c = this@ClipboardPanel.colors
            val m = (3 * d).toInt()
            val w = (resources.displayMetrics.widthPixels - (12 * d).toInt()) / 2 - 2 * m
            tv.layoutParams = RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(m, m, m, m) }
            tv.text = clip.text
            tv.setTextColor(c.keyText)
            tv.background = GradientDrawable().apply { cornerRadius = 16 * d; setColor(c.chip) }
            if (clip.pinned) {
                val pin = ResourcesCompat.getDrawable(resources, R.drawable.kb_pin, null)?.mutate()
                pin?.setBounds(0, 0, (16 * d).toInt(), (16 * d).toInt())
                pin?.colorFilter = PorterDuffColorFilter(c.selected, PorterDuff.Mode.SRC_IN)
                tv.setCompoundDrawablesRelative(null, null, pin, null)
            } else tv.setCompoundDrawablesRelative(null, null, null, null)
            tv.setOnClickListener { listener?.onFeedback(); listener?.onClip(clip.text) }
            tv.setOnLongClickListener { showActions(tv, clip); true }
        }
    }

    private val deleteTouch = OnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { listener?.onFeedback(); listener?.onDeleteDown(); v.isPressed = true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { listener?.onDeleteUp(); v.isPressed = false }
        }
        true
    }

    init {
        val circle = (34 * d).toInt()
        backBtn.setImageResource(R.drawable.kb_back)
        backBtn.scaleType = ImageView.ScaleType.CENTER
        backBtn.setOnClickListener { listener?.onFeedback(); listener?.onBack() }
        topBar.addView(backBtn, LinearLayout.LayoutParams(circle, circle).apply { marginStart = (8 * d).toInt(); marginEnd = (10 * d).toInt() })
        title.text = "Clipboard"
        title.typeface = font
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        topBar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        clearBtn.text = "Clear all"
        clearBtn.typeface = font
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        clearBtn.gravity = Gravity.CENTER
        clearBtn.setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
        clearBtn.setOnClickListener { listener?.onFeedback(); ClipStore.clearUnpinned(context); refresh() }
        topBar.addView(clearBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (34 * d).toInt()).apply { marginEnd = (10 * d).toInt() })
        column.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50 * d).toInt()))

        hint.text = "Tap a clip to paste it. Long-press to pin or delete. Clips that aren't pinned are removed after an hour."
        hint.typeface = font
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        hint.setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (6 * d).toInt())
        column.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val body = FrameLayout(context)
        list.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        list.adapter = adapter
        list.itemAnimator = null
        list.setPadding((6 * d).toInt(), 0, (6 * d).toInt(), (6 * d).toInt())
        list.clipToPadding = false
        body.addView(list, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        messageText.typeface = font
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        messageText.gravity = Gravity.CENTER
        messageText.setPadding((30 * d).toInt(), 0, (30 * d).toInt(), 0)
        messageBox.addView(messageText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        for (b in listOf(primaryBtn, secondaryBtn)) {
            b.typeface = font
            b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            b.gravity = Gravity.CENTER
            b.setPadding((22 * d).toInt(), 0, (22 * d).toInt(), 0)
            messageBox.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (42 * d).toInt()).apply { topMargin = (12 * d).toInt() })
        }
        body.addView(messageBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        abc.text = "ABC"
        abc.typeface = font
        abc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f)
        abc.gravity = Gravity.CENTER
        abc.setOnClickListener { listener?.onFeedback(); listener?.onBack() }
        bottomBar.addView(abc, LinearLayout.LayoutParams((64 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        bottomBar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        deleteBtn.setImageResource(R.drawable.kb_delete)
        deleteBtn.scaleType = ImageView.ScaleType.CENTER
        deleteBtn.setOnTouchListener(deleteTouch)
        bottomBar.addView(deleteBtn, LinearLayout.LayoutParams((64 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * d).toInt()))

        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim.setOnClickListener { hideActions() }
        addView(scrim, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(actions, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (46 * d).toInt()))
        applyColors()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((EmojiPanel.HEIGHT_DP * d).toInt(), MeasureSpec.EXACTLY))
    }

    /** Reload the cards. Called each time the panel opens. */
    fun refresh() {
        hideActions()
        if (!ClipStore.enabled(context)) {
            clips = emptyList()
            adapter.notifyDataSetChanged()
            hint.visibility = GONE
            clearBtn.visibility = GONE
            showMessage(
                "Clipboard history is off. Turn it on to keep text you copy for an hour, so you can paste it again. It stays on this phone.",
                "Turn on clipboard history", { ClipStore.setEnabled(context, true); captureCurrent(); refresh() },
                "Paste what's copied now", { currentClipText()?.let { listener?.onClip(it) } },
            )
            return
        }
        captureCurrent()
        clips = ClipStore.all(context)
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
        hint.visibility = if (clips.isEmpty()) GONE else VISIBLE
        clearBtn.visibility = if (clips.any { !it.pinned }) VISIBLE else GONE
        if (clips.isEmpty()) showMessage("Text you copy will show up here, ready to paste again.", null, null, null, null)
        else messageBox.visibility = GONE
    }

    private fun currentClipText(): String? = try {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    /** Whatever is copied right now, in case it was copied while the keyboard was not running. */
    private fun captureCurrent() {
        currentClipText()?.let { ClipStore.add(context, it) }
    }

    private fun showMessage(text: String, primary: String?, onPrimary: (() -> Unit)?, secondary: String?, onSecondary: (() -> Unit)?) {
        messageText.text = text
        for ((b, label, action) in listOf(Triple(primaryBtn, primary, onPrimary), Triple(secondaryBtn, secondary, onSecondary))) {
            b.visibility = if (label == null) GONE else VISIBLE
            b.text = label.orEmpty()
            b.setOnClickListener { listener?.onFeedback(); action?.invoke() }
        }
        messageBox.visibility = VISIBLE
    }

    // ---------------------------------------------------------------- pin / delete

    private fun hideActions() {
        actions.visibility = GONE
        scrim.visibility = GONE
    }

    @SuppressLint("SetTextI18n")
    private fun showActions(anchor: View, clip: ClipStore.Clip) {
        listener?.onFeedback()
        actions.removeAllViews()
        val c = colors
        actions.background = GradientDrawable().apply { cornerRadius = 100f * d; setColor(c.popupBg) }
        actions.setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
        actions.gravity = Gravity.CENTER_VERTICAL
        fun item(label: String, onTap: () -> Unit) = TextView(context).apply {
            text = label
            typeface = font
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setTextColor(c.popupText)
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
            setOnClickListener { hideActions(); listener?.onFeedback(); onTap() }
        }
        actions.addView(item(if (clip.pinned) "Unpin" else "Pin") { ClipStore.togglePin(context, clip); refresh() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        actions.addView(item("Delete") { ClipStore.delete(context, clip); refresh() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        actions.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2); val me = IntArray(2)
        anchor.getLocationInWindow(loc); getLocationInWindow(me)
        val w = actions.measuredWidth.toFloat()
        actions.x = ((loc[0] - me[0]) + anchor.width / 2f - w / 2f).coerceIn(4 * d, width - w - 4 * d)
        val above = (loc[1] - me[1]) - 50 * d
        actions.y = if (above > 4 * d) above else ((loc[1] - me[1]) + anchor.height + 4 * d)
        scrim.visibility = VISIBLE
        actions.visibility = VISIBLE
        actions.bringToFront()
    }

    // ---------------------------------------------------------------- colours

    private fun applyColors() {
        val c = colors
        setBackgroundColor(c.bg)
        backBtn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.chip) }
        backBtn.colorFilter = PorterDuffColorFilter(c.keyText, PorterDuff.Mode.SRC_IN)
        title.setTextColor(c.keyText)
        clearBtn.setTextColor(c.keyText)
        clearBtn.background = GradientDrawable().apply { cornerRadius = 100f * d; setColor(c.chip) }
        hint.setTextColor(c.hint)
        messageText.setTextColor(c.hint)
        primaryBtn.setTextColor(c.selectedText)
        primaryBtn.background = GradientDrawable().apply { cornerRadius = 100f * d; setColor(c.selected) }
        secondaryBtn.setTextColor(c.keyText)
        secondaryBtn.background = GradientDrawable().apply { cornerRadius = 100f * d; setColor(c.chip) }
        abc.setTextColor(c.keyText)
        deleteBtn.colorFilter = PorterDuffColorFilter(c.keyText, PorterDuff.Mode.SRC_IN)
        adapter.notifyDataSetChanged()
    }
}
