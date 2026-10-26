package com.paul.urdukb.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paul.urdukb.R

/** One emoji from the bundled list. */
class EmojiItem(val emoji: String, val skin: Boolean, val name: String)

object EmojiData {
    class Section(val id: String, val title: String, val icon: Int, val items: List<EmojiItem>)

    private var cached: List<Section>? = null

    private val titles = linkedMapOf(
        "smileys" to ("Smileys & Emotions" to R.drawable.kb_cat_smileys),
        "people" to ("People" to R.drawable.kb_cat_people),
        "nature" to ("Animals & Nature" to R.drawable.kb_cat_nature),
        "food" to ("Food & Drink" to R.drawable.kb_cat_food),
        "travel" to ("Travel & Places" to R.drawable.kb_cat_travel),
        "activities" to ("Activities" to R.drawable.kb_cat_activities),
        "objects" to ("Objects" to R.drawable.kb_cat_objects),
        "symbols" to ("Symbols" to R.drawable.kb_cat_symbols),
        "flags" to ("Flags" to R.drawable.kb_cat_flags),
    )

    /** Only the emoji this phone can actually draw, so there are no empty boxes. */
    fun load(ctx: Context): List<Section> {
        cached?.let { return it }
        val probe = Paint()
        val byGroup = LinkedHashMap<String, MutableList<EmojiItem>>()
        ctx.assets.open("emoji/emoji.tsv").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val p = line.split('\t')
                if (p.size < 4) return@forEach
                if (!probe.hasGlyph(p[1])) return@forEach
                byGroup.getOrPut(p[0]) { ArrayList() } += EmojiItem(p[1], p[2] == "1", p[3])
            }
        }
        val out = titles.mapNotNull { (id, tv) ->
            byGroup[id]?.let { Section(id, tv.first, tv.second, it) }
        }
        cached = out
        return out
    }

    fun loadEmoticons(ctx: Context): List<Pair<String, List<String>>> =
        ctx.assets.open("emoji/emoticons.tsv").bufferedReader().readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 2) null else p[0] to p.drop(1)
        }

    private val tones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

    /** Skin tone variants: the modifier goes straight after the first character. */
    fun variants(emoji: String): List<String> {
        val first = emoji.offsetByCodePoints(0, 1)
        val probe = Paint()
        return tones.map { emoji.substring(0, first) + it + emoji.substring(first) }.filter { probe.hasGlyph(it) }
    }
}

/**
 * Gboard's emoji screen: a back button and category icons on top, a scrolling grid with section
 * titles (Recent first), and a bottom bar with ABC, the emoji and emoticon pills and backspace.
 * Gboard's Emoji Kitchen, GIFs and stickers come from Google's online services and are not included.
 */
class EmojiPanel(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onEmoji(text: String)
        fun onBack()
        fun onDeleteDown()
        fun onDeleteUp()
        fun onFeedback()
    }

    companion object {
        const val HEIGHT_DP = 443f
        private const val SPAN = 24
        private const val RECENT_PREF = "recent_emoji"
        private const val MAX_RECENT = 32
    }

    var listener: Listener? = null
    var colors: KbColors = KbColors.of(context)
        set(v) { field = v; applyColors() }

    private val d = resources.displayMetrics.density
    private val font = ResourcesCompat.getFont(context, R.font.google_sans_text) ?: Typeface.DEFAULT
    private val handler = Handler(Looper.getMainLooper())

    private sealed class Row {
        class Header(val title: String, val section: Int) : Row()
        class Emoji(val item: EmojiItem) : Row()
        class Emoticon(val text: String) : Row()
    }

    private var rows: List<Row> = emptyList()
    private var sectionStarts: List<Int> = emptyList()   // adapter position of each section's header
    private var sectionIcons: List<Int> = emptyList()
    private var emoticonMode = false
    private var selectedSection = 0

    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val topBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val tabScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
    private val tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val backBtn = ImageView(context)
    private val list = RecyclerView(context)
    private val bottomBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val abc = TextView(context)
    private val emojiPill = ImageView(context)
    private val emoticonPill = TextView(context)
    private val deleteBtn = ImageView(context)
    private val tabViews = ArrayList<ImageView>()
    private val tonePicker = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; visibility = GONE }
    private val scrim = View(context).apply { visibility = GONE }

    private val manager = GridLayoutManager(context, SPAN).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = when (rows[position]) {
                is Row.Header -> SPAN
                is Row.Emoji -> 3
                is Row.Emoticon -> 8
            }
        }
    }

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.Emoji -> 1
            is Row.Emoticon -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(context).apply {
                gravity = if (viewType == 0) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
                typeface = font
                includeFontPadding = false
            }
            when (viewType) {
                0 -> {
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                    tv.setPadding((16 * d).toInt(), 0, 0, 0)
                    tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * d).toInt())
                }
                1 -> {
                    tv.typeface = Typeface.DEFAULT
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32f)
                    tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50.4f * d).toInt())
                }
                else -> {
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
                    tv.maxLines = 1
                    tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50.4f * d).toInt())
                }
            }
            return object : RecyclerView.ViewHolder(tv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val tv = holder.itemView as TextView
            val c = colors
            when (val r = rows[position]) {
                is Row.Header -> { tv.text = r.title; tv.setTextColor(c.hint); tv.setOnClickListener(null); tv.setOnLongClickListener(null) }
                is Row.Emoji -> {
                    tv.text = r.item.emoji
                    tv.setOnClickListener { listener?.onFeedback(); pick(r.item.emoji) }
                    if (r.item.skin) tv.setOnLongClickListener { showTones(tv, r.item); true } else tv.setOnLongClickListener(null)
                }
                is Row.Emoticon -> {
                    tv.text = r.text
                    tv.setTextColor(c.keyText)
                    tv.setOnClickListener { listener?.onFeedback(); listener?.onEmoji(r.text) }
                    tv.setOnLongClickListener(null)
                }
            }
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
        val tab = (34 * d).toInt()
        // top bar
        backBtn.setImageResource(R.drawable.kb_back)
        backBtn.scaleType = ImageView.ScaleType.CENTER
        backBtn.setOnClickListener { listener?.onFeedback(); listener?.onBack() }
        topBar.addView(backBtn, LinearLayout.LayoutParams(tab, tab).apply { marginStart = (8 * d).toInt(); marginEnd = (8 * d).toInt() })
        tabScroll.addView(tabRow)
        topBar.addView(tabScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50 * d).toInt()))

        // list
        list.layoutManager = manager
        list.adapter = adapter
        list.itemAnimator = null
        list.setHasFixedSize(true)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val first = manager.findFirstVisibleItemPosition()
                val s = sectionStarts.indexOfLast { it <= first }.coerceAtLeast(0)
                if (s != selectedSection) { selectedSection = s; paintTabs() }
            }
        })
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // bottom bar
        abc.text = "ABC"
        abc.typeface = font
        abc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f)
        abc.gravity = Gravity.CENTER
        abc.setOnClickListener { listener?.onFeedback(); listener?.onBack() }
        bottomBar.addView(abc, LinearLayout.LayoutParams((72 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        emojiPill.setImageResource(R.drawable.kb_emoji)
        emojiPill.scaleType = ImageView.ScaleType.CENTER
        emojiPill.setOnClickListener { setEmoticons(false) }
        bottomBar.addView(emojiPill, LinearLayout.LayoutParams((64 * d).toInt(), (38 * d).toInt()))
        emoticonPill.text = ":-)"
        emoticonPill.typeface = font
        emoticonPill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f)
        emoticonPill.gravity = Gravity.CENTER
        emoticonPill.setOnClickListener { setEmoticons(true) }
        bottomBar.addView(emoticonPill, LinearLayout.LayoutParams((64 * d).toInt(), (38 * d).toInt()).apply { marginStart = (5 * d).toInt() })
        bottomBar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        deleteBtn.setImageResource(R.drawable.kb_delete)
        deleteBtn.scaleType = ImageView.ScaleType.CENTER
        deleteBtn.setOnTouchListener(deleteTouch)
        bottomBar.addView(deleteBtn, LinearLayout.LayoutParams((72 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * d).toInt()))

        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // skin tone picker on top of everything
        scrim.setOnClickListener { hideTones() }
        addView(scrim, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(tonePicker, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (52 * d).toInt()))

        buildEmoji()
        applyColors()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((HEIGHT_DP * d).toInt(), MeasureSpec.EXACTLY))
    }

    // ---------------------------------------------------------------- data

    private fun recents(): List<String> =
        Kb.prefs(context).getString(RECENT_PREF, "").orEmpty().split('|').filter { it.isNotEmpty() }

    private fun pick(emoji: String) {
        val now = (listOf(emoji) + recents().filter { it != emoji }).take(MAX_RECENT)
        Kb.prefs(context).edit().putString(RECENT_PREF, now.joinToString("|")).apply()
        listener?.onEmoji(emoji)
    }

    /** Called each time the panel opens, so recents are fresh and the view starts at the top. */
    fun refresh() {
        hideTones()
        if (emoticonMode) setEmoticons(true) else buildEmoji()
    }

    private fun buildEmoji() {
        emoticonMode = false
        val out = ArrayList<Row>()
        val starts = ArrayList<Int>()
        val icons = ArrayList<Int>()
        val rec = recents()
        if (rec.isNotEmpty()) {
            starts += out.size; icons += R.drawable.kb_cat_recent
            out += Row.Header("Recent Emoji", starts.size - 1)
            rec.forEach { out += Row.Emoji(EmojiItem(it, false, "")) }
        }
        EmojiData.load(context).forEach { s ->
            starts += out.size; icons += s.icon
            out += Row.Header(s.title, starts.size - 1)
            s.items.forEach { out += Row.Emoji(it) }
        }
        rows = out
        sectionStarts = starts
        sectionIcons = icons
        selectedSection = 0
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
        buildTabs()
        paintBottom()
    }

    fun setEmoticons(on: Boolean) {
        if (!on) { buildEmoji(); return }
        emoticonMode = true
        val out = ArrayList<Row>()
        val starts = ArrayList<Int>()
        EmojiData.loadEmoticons(context).forEach { (title, items) ->
            starts += out.size
            out += Row.Header(title, starts.size - 1)
            items.forEach { out += Row.Emoticon(it) }
        }
        rows = out
        sectionStarts = starts
        sectionIcons = emptyList()
        selectedSection = 0
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
        buildTabs()
        paintBottom()
    }

    // ---------------------------------------------------------------- tabs

    private fun buildTabs() {
        tabRow.removeAllViews()
        tabViews.clear()
        val size = (34 * d).toInt()
        sectionIcons.forEachIndexed { i, icon ->
            val iv = ImageView(context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.CENTER
                setOnClickListener {
                    listener?.onFeedback()
                    selectedSection = i
                    paintTabs()
                    manager.scrollToPositionWithOffset(sectionStarts[i], 0)
                }
            }
            tabViews += iv
            tabRow.addView(iv, LinearLayout.LayoutParams(size, size).apply { marginEnd = (1 * d).toInt() })
        }
        paintTabs()
    }

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun pill(color: Int) = GradientDrawable().apply { cornerRadius = 100f * d; setColor(color) }

    private fun paintTabs() {
        val c = colors
        tabViews.forEachIndexed { i, iv ->
            val sel = i == selectedSection
            iv.background = if (sel) circle(c.selected) else null
            iv.colorFilter = PorterDuffColorFilter(if (sel) c.selectedText else c.hint, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun paintBottom() {
        val c = colors
        emojiPill.background = pill(if (!emoticonMode) c.selected else c.chip)
        emojiPill.colorFilter = PorterDuffColorFilter(if (!emoticonMode) c.selectedText else c.keyText, PorterDuff.Mode.SRC_IN)
        emoticonPill.background = pill(if (emoticonMode) c.selected else c.chip)
        emoticonPill.setTextColor(if (emoticonMode) c.selectedText else c.keyText)
    }

    private fun applyColors() {
        val c = colors
        setBackgroundColor(c.bg)
        backBtn.background = circle(c.chip)
        backBtn.colorFilter = PorterDuffColorFilter(c.keyText, PorterDuff.Mode.SRC_IN)
        abc.setTextColor(c.keyText)
        deleteBtn.colorFilter = PorterDuffColorFilter(c.keyText, PorterDuff.Mode.SRC_IN)
        paintTabs()
        paintBottom()
        adapter.notifyDataSetChanged()
    }

    // ---------------------------------------------------------------- skin tones

    private fun hideTones() {
        tonePicker.visibility = GONE
        scrim.visibility = GONE
    }

    @SuppressLint("SetTextI18n")
    private fun showTones(anchor: View, item: EmojiItem) {
        val options = listOf(item.emoji) + EmojiData.variants(item.emoji)
        if (options.size < 2) return
        listener?.onFeedback()
        tonePicker.removeAllViews()
        val c = colors
        tonePicker.background = pill(c.popupBg)
        tonePicker.setPadding((6 * d).toInt(), 0, (6 * d).toInt(), 0)
        tonePicker.gravity = Gravity.CENTER_VERTICAL
        options.forEach { e ->
            tonePicker.addView(TextView(context).apply {
                text = e
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28f)
                setOnClickListener { hideTones(); listener?.onFeedback(); pick(e) }
            }, LinearLayout.LayoutParams((44 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val loc = IntArray(2); val me = IntArray(2)
        anchor.getLocationInWindow(loc); getLocationInWindow(me)
        val w = (options.size * 44 + 12) * d
        var x = (loc[0] - me[0]) + anchor.width / 2f - w / 2f
        x = x.coerceIn(4 * d, width - w - 4 * d)
        val y = ((loc[1] - me[1]) - 56 * d).coerceAtLeast(2 * d)
        tonePicker.x = x
        tonePicker.y = y
        scrim.visibility = VISIBLE
        tonePicker.visibility = VISIBLE
        tonePicker.bringToFront()
    }
}
