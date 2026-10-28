package com.paul.urdukb.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
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
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paul.urdukb.R

/** One emoji from the bundled list. */
class EmojiItem(val emoji: String, val skin: Boolean, val name: String)

object EmojiData {
    class Section(val id: String, val title: String, val icon: Int, val items: List<EmojiItem>)

    @Volatile private var cached: List<Section>? = null

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
    @Synchronized
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

    private val synonyms = mapOf(
        "lol" to listOf("joy", "rolling on the floor"), "haha" to listOf("joy", "grinning", "rolling on the floor"),
        "laugh" to listOf("joy", "grinning", "rolling on the floor"), "love" to listOf("heart", "kiss", "love"),
        "sad" to listOf("cry", "sad", "pensive", "disappointed"), "happy" to listOf("smil", "grin"),
        "angry" to listOf("angry", "rage", "pouting"), "mad" to listOf("angry", "rage", "pouting"),
        "ok" to listOf("ok hand", "thumbs up"), "thanks" to listOf("folded hands"), "thank" to listOf("folded hands"),
        "pray" to listOf("folded hands"), "dua" to listOf("folded hands"), "eid" to listOf("crescent", "mosque", "star and crescent"),
        "cool" to listOf("sunglasses"), "yes" to listOf("thumbs up", "check mark"), "no" to listOf("thumbs down", "cross mark", "prohibited"),
        "party" to listOf("party", "confetti", "balloon", "tada"), "sleep" to listOf("sleep", "zzz"), "tired" to listOf("sleep", "yawning", "weary", "tired"),
        "food" to listOf("pizza", "hamburger", "food", "rice", "curry"), "chai" to listOf("teacup", "hot beverage"), "tea" to listOf("teacup", "hot beverage"),
        "smile" to listOf("smil", "grin"), "wow" to listOf("astonished", "star-struck", "exploding head"), "bye" to listOf("waving hand"), "hi" to listOf("waving hand"),
        "hello" to listOf("waving hand"), "think" to listOf("thinking"), "money" to listOf("money", "dollar", "coin"), "fire" to listOf("fire"),
    )

    /** Emoji whose name contains every word typed (with a few everyday synonyms), best matches first. */
    fun search(ctx: Context, query: String): List<EmojiItem> {
        val words = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, EmojiItem>>()
        for (section in load(ctx)) for (item in section.items) {
            val name = item.name.lowercase()
            var total = 0
            var all = true
            for (w in words) {
                val options = synonyms[w] ?: listOf(w)
                var best = -1
                for (o in options) {
                    val i = name.indexOf(o)
                    if (i < 0) continue
                    val score = if (i == 0) 0 else if (name[i - 1] == ' ') 1 else 2
                    if (best < 0 || score < best) best = score
                }
                if (best < 0) { all = false; break }
                total += best
            }
            if (all) scored += total to item
        }
        return scored.sortedBy { it.first }.map { it.second }.take(80)
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
 * Gboard's emoji screen: emoji, GIFs (GIPHY), stickers (GIPHY) and text emoticons, each with its own tab at the
 * bottom. Emoji and GIF/sticker screens have a search field; tapping it shrinks the panel to a strip of results above
 * the normal letter keys, and what you type goes into the search instead of the app.
 */
class EmojiPanel(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onEmoji(text: String)
        fun onBack()
        fun onDeleteDown()
        fun onDeleteUp()
        fun onFeedback()
        fun onSearchState(active: Boolean)
        fun onMedia(item: GifItem)
        fun onOpenApp()
    }

    enum class Mode { EMOJI, GIF, STICKER, EMOTICON }

    companion object {
        const val HEIGHT_DP = 443f
        const val COMPACT_DP = 176f
        private const val SPAN = 24
        private const val RECENT_PREF = "recent_emoji"
        private const val MAX_RECENT = 32
        private const val DEBOUNCE_MS = 1100L

        /** Search words shown as chips on the GIF and sticker screens; the first is the trending feed. */
        private val MEDIA_CHIPS = listOf(
            "Trending" to "", "Happy" to "happy", "Love" to "love", "Haha" to "haha", "Sad" to "sad", "Wow" to "wow",
            "Thanks" to "thank you", "Yes" to "yes", "No" to "no", "OK" to "ok", "Hello" to "hello", "Bye" to "bye",
            "Eid Mubarak" to "eid mubarak", "Birthday" to "happy birthday", "Good night" to "good night",
        )
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

    var mode = Mode.EMOJI
        private set
    var searching = false
        private set
    private var query = StringBuilder()
    private var selectedTab = 0
    private var loadToken = 0

    private var rows: List<Row> = emptyList()
    private var sectionStarts: List<Int> = emptyList()
    private var sectionIcons: List<Int> = emptyList()
    private var emoticonCats: List<Pair<String, List<String>>> = emptyList()
    private var mediaItems: List<GifItem> = emptyList()
    private var stripItems: List<Any> = emptyList()

    // ------------------------------------------------------------ views
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val topBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val backBtn = ImageView(context)
    private val searchPill = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val searchIcon = ImageView(context)
    private val searchText = TextView(context)
    private val clearBtn = ImageView(context)
    private val titleView = TextView(context)
    private val tabScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
    private val tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val content = FrameLayout(context)
    private val list = RecyclerView(context)
    private val mediaList = RecyclerView(context)
    private val strip = RecyclerView(context)
    private val messageBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = GONE }
    private val messageText = TextView(context)
    private val messageBtn = TextView(context)
    private val attribution = TextView(context)
    private val bottomBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val abc = TextView(context)
    private val emojiPill = ImageView(context)
    private val gifPill = TextView(context)
    private val stickerPill = ImageView(context)
    private val emoticonPill = TextView(context)
    private val deleteBtn = ImageView(context)
    private val tabViews = ArrayList<View>()
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

    /** GIF (staggered columns) and sticker (square grid) results. */
    private val mediaAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = mediaItems.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val iv = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, 10 * d)
                }
            }
            return object : RecyclerView.ViewHolder(iv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val iv = holder.itemView as ImageView
            val item = mediaItems[position]
            val cols = if (mode == Mode.STICKER) 4 else 3
            val m = (2 * d).toInt()
            val w = (resources.displayMetrics.widthPixels - (8 * d).toInt()) / cols - 2 * m
            val h = if (item.sticker) w else (w * item.height / item.width.coerceAtLeast(1)).coerceIn((70 * d).toInt(), (230 * d).toInt())
            iv.layoutParams = RecyclerView.LayoutParams(w, h).apply { setMargins(m, m, m, m) }
            iv.background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(this@EmojiPanel.colors.chip) }
            iv.scaleType = if (item.sticker) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            MediaCache.show(iv, item.thumbUrl)
            iv.setOnClickListener { listener?.onFeedback(); listener?.onMedia(item) }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) = MediaCache.release(holder.itemView as ImageView)
    }

    /** The one-row result strip shown while searching. */
    private val stripAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = stripItems.size
        override fun getItemViewType(position: Int) = if (stripItems[position] is EmojiItem) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v: View = if (viewType == 0) TextView(context).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32f)
                layoutParams = RecyclerView.LayoutParams((54 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            } else ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, 10 * d)
                }
            }
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val entry = stripItems[position]) {
                is EmojiItem -> (holder.itemView as TextView).apply {
                    text = entry.emoji
                    setOnClickListener { listener?.onFeedback(); pick(entry.emoji) }
                }
                is GifItem -> (holder.itemView as ImageView).apply {
                    val h = (110 * d).toInt()   // fixed, so tiles never change size after the first layout
                    val w = if (entry.sticker) h else (h * entry.width / entry.height.coerceAtLeast(1)).coerceIn((70 * d).toInt(), (220 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(w, h).apply { setMargins((2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt()) }
                    background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(this@EmojiPanel.colors.chip) }
                    scaleType = if (entry.sticker) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                    MediaCache.show(this, entry.thumbUrl)
                    setOnClickListener { listener?.onFeedback(); listener?.onMedia(entry) }
                }
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder.itemView as? ImageView)?.let { MediaCache.release(it) }
        }
    }

    private val deleteTouch = OnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { listener?.onFeedback(); listener?.onDeleteDown(); v.isPressed = true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { listener?.onDeleteUp(); v.isPressed = false }
        }
        true
    }

    private val searchRunnable = Runnable { runSearch(immediate = true) }

    init {
        val circle = (34 * d).toInt()
        // top bar: back, search field (or the title on the emoticon screen)
        backBtn.setImageResource(R.drawable.kb_back)
        backBtn.scaleType = ImageView.ScaleType.CENTER
        backBtn.setOnClickListener { listener?.onFeedback(); if (searching) closeSearch() else listener?.onBack() }
        topBar.addView(backBtn, LinearLayout.LayoutParams(circle, circle).apply { marginStart = (8 * d).toInt(); marginEnd = (8 * d).toInt() })

        searchIcon.setImageResource(R.drawable.kb_search)
        searchIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        searchPill.addView(searchIcon, LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()).apply { marginStart = (12 * d).toInt() })
        searchText.typeface = font
        searchText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        searchText.maxLines = 1
        searchText.ellipsize = android.text.TextUtils.TruncateAt.START
        searchText.gravity = Gravity.CENTER_VERTICAL
        searchPill.addView(searchText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = (10 * d).toInt() })
        clearBtn.setImageResource(R.drawable.kb_close)
        clearBtn.scaleType = ImageView.ScaleType.FIT_CENTER
        clearBtn.setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        clearBtn.visibility = GONE
        clearBtn.setOnClickListener { listener?.onFeedback(); query.clear(); onQueryChanged() }
        searchPill.addView(clearBtn, LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt()).apply { marginEnd = (8 * d).toInt() })
        searchPill.setOnClickListener { if (!searching) { listener?.onFeedback(); openSearch() } }
        topBar.addView(searchPill, LinearLayout.LayoutParams(0, (38 * d).toInt(), 1f).apply { marginEnd = (10 * d).toInt() })

        titleView.typeface = font
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        titleView.text = "Emoticons"
        titleView.visibility = GONE
        topBar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50 * d).toInt()))

        // category icons / chips
        tabScroll.addView(tabRow)
        column.addView(tabScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (42 * d).toInt()).apply { marginStart = (8 * d).toInt() })

        // content
        list.layoutManager = manager
        list.adapter = adapter
        list.itemAnimator = null
        list.setHasFixedSize(true)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (mode != Mode.EMOJI || sectionStarts.isEmpty()) return
                val first = manager.findFirstVisibleItemPosition()
                val s = sectionStarts.indexOfLast { it <= first }.coerceAtLeast(0)
                if (s != selectedTab) { selectedTab = s; paintTabs(); scrollTabIntoView() }
            }
        })
        content.addView(list, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        mediaList.itemAnimator = null
        mediaList.adapter = mediaAdapter
        mediaList.setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (4 * d).toInt())
        mediaList.clipToPadding = false
        mediaList.visibility = GONE
        content.addView(mediaList, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        strip.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        strip.adapter = stripAdapter
        strip.itemAnimator = null
        strip.setPadding((6 * d).toInt(), 0, (6 * d).toInt(), 0)
        strip.clipToPadding = false
        strip.visibility = GONE
        content.addView(strip, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        messageText.typeface = font
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        messageText.gravity = Gravity.CENTER
        messageText.setPadding((28 * d).toInt(), 0, (28 * d).toInt(), 0)
        messageBox.addView(messageText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        messageBtn.typeface = font
        messageBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        messageBtn.gravity = Gravity.CENTER
        messageBtn.setPadding((22 * d).toInt(), 0, (22 * d).toInt(), 0)
        messageBtn.setOnClickListener { listener?.onFeedback(); listener?.onOpenApp() }
        messageBox.addView(messageBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (40 * d).toInt()).apply { topMargin = (12 * d).toInt() })
        content.addView(messageBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        attribution.text = "Powered by GIPHY"
        attribution.typeface = font
        attribution.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f)
        attribution.setPadding((9 * d).toInt(), (3 * d).toInt(), (9 * d).toInt(), (3 * d).toInt())
        attribution.visibility = GONE
        content.addView(attribution, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = (10 * d).toInt(); bottomMargin = (6 * d).toInt()
        })
        column.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // bottom bar: ABC | emoji GIF sticker emoticon | delete
        abc.text = "ABC"
        abc.typeface = font
        abc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f)
        abc.gravity = Gravity.CENTER
        abc.setOnClickListener { listener?.onFeedback(); listener?.onBack() }
        bottomBar.addView(abc, LinearLayout.LayoutParams((64 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        val pw = (56 * d).toInt()
        val ph = (38 * d).toInt()
        val gap = (4 * d).toInt()
        emojiPill.setImageResource(R.drawable.kb_emoji)
        emojiPill.scaleType = ImageView.ScaleType.CENTER
        emojiPill.setOnClickListener { listener?.onFeedback(); setMode(Mode.EMOJI) }
        bottomBar.addView(emojiPill, LinearLayout.LayoutParams(pw, ph))
        gifPill.text = "GIF"
        gifPill.typeface = font
        gifPill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        gifPill.gravity = Gravity.CENTER
        gifPill.setOnClickListener { listener?.onFeedback(); setMode(Mode.GIF) }
        bottomBar.addView(gifPill, LinearLayout.LayoutParams(pw, ph).apply { marginStart = gap })
        stickerPill.setImageResource(R.drawable.kb_sticker)
        stickerPill.scaleType = ImageView.ScaleType.CENTER
        stickerPill.setOnClickListener { listener?.onFeedback(); setMode(Mode.STICKER) }
        bottomBar.addView(stickerPill, LinearLayout.LayoutParams(pw, ph).apply { marginStart = gap })
        emoticonPill.text = ":-)"
        emoticonPill.typeface = font
        emoticonPill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f)
        emoticonPill.gravity = Gravity.CENTER
        emoticonPill.setOnClickListener { listener?.onFeedback(); setMode(Mode.EMOTICON) }
        bottomBar.addView(emoticonPill, LinearLayout.LayoutParams(pw, ph).apply { marginStart = gap })
        bottomBar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        deleteBtn.setImageResource(R.drawable.kb_delete)
        deleteBtn.scaleType = ImageView.ScaleType.CENTER
        deleteBtn.setOnTouchListener(deleteTouch)
        bottomBar.addView(deleteBtn, LinearLayout.LayoutParams((64 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * d).toInt()))

        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // skin tone picker on top of everything
        scrim.setOnClickListener { hideTones() }
        addView(scrim, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(tonePicker, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (52 * d).toInt()))

        applyColors()
    }

    private var built = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (searching) COMPACT_DP else HEIGHT_DP
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((h * d).toInt(), MeasureSpec.EXACTLY))
    }

    // ---------------------------------------------------------------- data

    private fun recents(): List<String> =
        Kb.prefs(context).getString(RECENT_PREF, "").orEmpty().split('|').filter { it.isNotEmpty() }

    private fun pick(emoji: String) {
        val now = (listOf(emoji) + recents().filter { it != emoji }).take(MAX_RECENT)
        Kb.prefs(context).edit().putString(RECENT_PREF, now.joinToString("|")).apply()
        listener?.onEmoji(emoji)
    }

    /** Called each time the panel opens, so recents are fresh. */
    fun refresh() {
        hideTones()
        if (searching) closeSearch()
        // built the first time the screen opens, not when the keyboard starts (the emoji list is large)
        if (!built || mode == Mode.EMOJI) setMode(mode)
    }

    /** For the emulator preview hooks. */
    fun showMode(name: String) = setMode(runCatching { Mode.valueOf(name.uppercase()) }.getOrDefault(Mode.EMOJI))

    /** For the emulator preview hooks. */
    fun openSearchFromHook() = openSearch()

    fun setEmoticons(on: Boolean) = setMode(if (on) Mode.EMOTICON else Mode.EMOJI)

    // ---------------------------------------------------------------- modes

    private fun setMode(m: Mode) {
        built = true
        if (searching) closeSearch()
        mode = m
        selectedTab = 0
        loadToken++
        handler.removeCallbacks(searchRunnable)
        list.visibility = GONE
        mediaList.visibility = GONE
        messageBox.visibility = GONE
        attribution.visibility = if (m == Mode.GIF || m == Mode.STICKER) VISIBLE else GONE
        searchPill.visibility = if (m == Mode.EMOTICON) GONE else VISIBLE
        titleView.visibility = if (m == Mode.EMOTICON) VISIBLE else GONE
        updateHint()
        when (m) {
            Mode.EMOJI -> buildEmoji()
            Mode.EMOTICON -> buildEmoticons()
            Mode.GIF, Mode.STICKER -> {
                mediaList.layoutManager = if (m == Mode.STICKER) GridLayoutManager(context, 4)
                else StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
                buildTabs()
                loadMedia(m == Mode.STICKER, MEDIA_CHIPS[0].second, toStrip = false)
            }
        }
        paintBottom()
    }

    private fun updateHint() {
        searchText.hint = when (mode) {
            Mode.GIF -> "Search GIFs"
            Mode.STICKER -> "Search stickers"
            else -> "Search emoji"
        }
    }

    private fun buildEmoji() {
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
        list.visibility = VISIBLE
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
        buildTabs()
    }

    private fun buildEmoticons() {
        if (emoticonCats.isEmpty()) emoticonCats = EmojiData.loadEmoticons(context)
        showEmoticonCategory(0)
        buildTabs()
    }

    private fun showEmoticonCategory(i: Int) {
        selectedTab = i
        rows = emoticonCats.getOrNull(i)?.second.orEmpty().map { Row.Emoticon(it) }
        sectionStarts = emptyList()
        list.visibility = VISIBLE
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
    }

    // ---------------------------------------------------------------- GIFs and stickers

    private fun showMessage(text: String, button: String? = null) {
        messageText.text = text
        messageBtn.text = button.orEmpty()
        messageBtn.visibility = if (button == null) GONE else VISIBLE
        messageBox.visibility = VISIBLE
    }

    private fun loadMedia(sticker: Boolean, q: String, toStrip: Boolean) {
        val token = ++loadToken
        if (toStrip) strip.visibility = GONE else mediaList.visibility = GONE
        if (!GiphyApi.hasKey(context)) {
            showMessage("GIFs and stickers come from GIPHY. Add a free GIPHY key in the app to turn them on.", "Open app")
            return
        }
        showMessage("Loading…")
        Thread {
            val result = try { GiphyApi.fetch(context, sticker, q) } catch (e: GiphyException) { e } catch (e: Exception) { GiphyException(GiphyException.Kind.NETWORK, e.message ?: "") }
            handler.post {
                if (token != loadToken) return@post
                if (result is GiphyException) {
                    when (result.kind) {
                        GiphyException.Kind.NO_KEY -> showMessage("GIFs and stickers come from GIPHY. Add a free GIPHY key in the app to turn them on.", "Open app")
                        GiphyException.Kind.BAD_KEY -> showMessage("GIPHY did not accept the key. Check it in the app.", "Open app")
                        GiphyException.Kind.LIMIT -> showMessage("GIPHY's free limit was reached. Try again in a little while.")
                        GiphyException.Kind.NETWORK -> showMessage("Couldn't reach GIPHY. Check your connection.")
                    }
                    return@post
                }
                @Suppress("UNCHECKED_CAST")
                val items = result as List<GifItem>
                if (items.isEmpty()) { showMessage("No results"); return@post }
                messageBox.visibility = GONE
                if (toStrip) {
                    stripItems = items
                    stripAdapter.notifyDataSetChanged()
                    strip.scrollToPosition(0)
                    strip.visibility = VISIBLE
                } else {
                    mediaItems = items
                    mediaAdapter.notifyDataSetChanged()
                    mediaList.scrollToPosition(0)
                    mediaList.visibility = VISIBLE
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- search

    private fun openSearch() {
        searching = true
        loadToken++   // a GIF list that is still loading for the browse screen must not appear over the search
        query.clear()
        hideTones()
        tabScroll.visibility = GONE
        bottomBar.visibility = GONE
        list.visibility = GONE
        mediaList.visibility = GONE
        messageBox.visibility = GONE
        attribution.visibility = GONE
        strip.visibility = GONE
        stripItems = emptyList()
        stripAdapter.notifyDataSetChanged()
        paintSearchField()
        listener?.onSearchState(true)
        requestLayout()
        if (mode == Mode.EMOJI) {
            stripItems = recents().map { EmojiItem(it, false, "") }
            stripAdapter.notifyDataSetChanged()
            strip.visibility = if (stripItems.isEmpty()) GONE else VISIBLE
            if (stripItems.isEmpty()) showMessage("Type to find an emoji")
        } else {
            showMessage("Type what you're looking for, then tap search")
        }
    }

    fun closeSearch() {
        if (!searching) return
        searching = false
        handler.removeCallbacks(searchRunnable)
        loadToken++
        strip.visibility = GONE
        messageBox.visibility = GONE
        tabScroll.visibility = VISIBLE
        bottomBar.visibility = VISIBLE
        paintSearchField()
        listener?.onSearchState(false)
        requestLayout()
        // put the browsing screen back
        when (mode) {
            Mode.EMOJI -> list.visibility = VISIBLE
            Mode.GIF, Mode.STICKER -> {
                attribution.visibility = VISIBLE
                if (mediaItems.isNotEmpty()) mediaList.visibility = VISIBLE else loadMedia(mode == Mode.STICKER, MEDIA_CHIPS.getOrNull(selectedTab)?.second.orEmpty(), toStrip = false)
            }
            Mode.EMOTICON -> list.visibility = VISIBLE
        }
    }

    /** Typed on the letter keys while the search is open. */
    fun searchType(s: String) {
        if (!searching) return
        query.append(s)
        onQueryChanged()
    }

    fun searchBackspace() {
        if (!searching || query.isEmpty()) return
        query.setLength(query.offsetByCodePoints(query.length, -1))
        onQueryChanged()
    }

    /** The search/enter key. */
    fun searchSubmit() {
        if (!searching) return
        handler.removeCallbacks(searchRunnable)
        runSearch(immediate = true)
    }

    private fun onQueryChanged() {
        paintSearchField()
        handler.removeCallbacks(searchRunnable)
        if (mode == Mode.EMOJI) runSearch(immediate = true)
        else if (query.isNotBlank()) handler.postDelayed(searchRunnable, DEBOUNCE_MS)
    }

    private fun runSearch(immediate: Boolean) {
        if (!searching) return
        val q = query.toString().trim()
        if (mode == Mode.EMOJI) {
            if (q.isEmpty()) {
                stripItems = recents().map { EmojiItem(it, false, "") }
                messageBox.visibility = GONE
            } else {
                stripItems = EmojiData.search(context, q)
                if (stripItems.isEmpty()) showMessage("No emoji for “$q”") else messageBox.visibility = GONE
            }
            stripAdapter.notifyDataSetChanged()
            strip.scrollToPosition(0)
            strip.visibility = if (stripItems.isEmpty()) GONE else VISIBLE
        } else if (q.isNotEmpty() && immediate) {
            loadMedia(mode == Mode.STICKER, q, toStrip = true)
        }
    }

    private fun paintSearchField() {
        val c = colors
        searchText.text = if (searching) query.toString() else ""
        searchText.setTextColor(c.keyText)
        searchText.setHintTextColor(c.hint)
        clearBtn.visibility = if (searching && query.isNotEmpty()) VISIBLE else GONE
        backBtn.contentDescription = if (searching) "Close search" else "Back"
        searchPill.background = pill(if (searching) c.selected.withAlpha(0.35f) else c.chip)
    }

    private fun Int.withAlpha(f: Float) = (this and 0x00FFFFFF) or ((f * 255).toInt() shl 24)

    // ---------------------------------------------------------------- tabs

    private fun buildTabs() {
        tabRow.removeAllViews()
        tabViews.clear()
        when (mode) {
            Mode.EMOJI -> {
                val size = (34 * d).toInt()
                sectionIcons.forEachIndexed { i, icon ->
                    val iv = ImageView(context).apply {
                        setImageResource(icon)
                        scaleType = ImageView.ScaleType.CENTER
                        setOnClickListener {
                            listener?.onFeedback()
                            selectedTab = i
                            paintTabs()
                            manager.scrollToPositionWithOffset(sectionStarts[i], 0)
                        }
                    }
                    tabViews += iv
                    tabRow.addView(iv, LinearLayout.LayoutParams(size, size).apply { marginEnd = (1 * d).toInt() })
                }
            }
            Mode.EMOTICON -> emoticonCats.forEachIndexed { i, (title, _) ->
                addChip(title) { showEmoticonCategory(i); paintTabs() }
            }
            Mode.GIF, Mode.STICKER -> MEDIA_CHIPS.forEachIndexed { i, (label, q) ->
                addChip(label) {
                    selectedTab = i
                    paintTabs()
                    loadMedia(mode == Mode.STICKER, q, toStrip = false)
                }
            }
        }
        tabScroll.scrollTo(0, 0)
        paintTabs()
    }

    private fun addChip(label: String, onTap: () -> Unit) {
        val tv = TextView(context).apply {
            text = label
            typeface = font
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
            setOnClickListener { listener?.onFeedback(); onTap() }
        }
        tabViews += tv
        tabRow.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (32 * d).toInt()).apply { marginEnd = (6 * d).toInt() })
    }

    private fun scrollTabIntoView() {
        val v = tabViews.getOrNull(selectedTab) ?: return
        tabScroll.post { tabScroll.smoothScrollTo((v.left - tabScroll.width / 2 + v.width / 2).coerceAtLeast(0), 0) }
    }

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun pill(color: Int) = GradientDrawable().apply { cornerRadius = 100f * d; setColor(color) }

    private fun paintTabs() {
        val c = colors
        tabViews.forEachIndexed { i, v ->
            val sel = i == selectedTab
            if (v is ImageView) {
                v.background = if (sel) circle(c.selected) else null
                v.colorFilter = PorterDuffColorFilter(if (sel) c.selectedText else c.hint, PorterDuff.Mode.SRC_IN)
            } else if (v is TextView) {
                v.background = pill(if (sel) c.selected else c.chip)
                v.setTextColor(if (sel) c.selectedText else c.keyText)
            }
        }
    }

    private fun paintBottom() {
        val c = colors
        fun style(v: View, on: Boolean) {
            v.background = pill(if (on) c.selected else c.chip)
            val fg = if (on) c.selectedText else c.keyText
            when (v) {
                is ImageView -> v.colorFilter = PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN)
                is TextView -> v.setTextColor(fg)
            }
        }
        style(emojiPill, mode == Mode.EMOJI)
        style(gifPill, mode == Mode.GIF)
        style(stickerPill, mode == Mode.STICKER)
        style(emoticonPill, mode == Mode.EMOTICON)
    }

    private fun applyColors() {
        val c = colors
        setBackgroundColor(c.bg)
        backBtn.background = circle(c.chip)
        backBtn.colorFilter = PorterDuffColorFilter(c.keyText, PorterDuff.Mode.SRC_IN)
        searchIcon.colorFilter = PorterDuffColorFilter(c.hint, PorterDuff.Mode.SRC_IN)
        clearBtn.colorFilter = PorterDuffColorFilter(c.hint, PorterDuff.Mode.SRC_IN)
        titleView.setTextColor(c.keyText)
        abc.setTextColor(c.keyText)
        deleteBtn.colorFilter = PorterDuffColorFilter(c.keyText, PorterDuff.Mode.SRC_IN)
        messageText.setTextColor(c.hint)
        messageBtn.setTextColor(c.selectedText)
        messageBtn.background = pill(c.selected)
        attribution.setTextColor(c.hint)
        attribution.background = pill(c.popupBg)
        paintSearchField()
        paintTabs()
        paintBottom()
        adapter.notifyDataSetChanged()
        mediaAdapter.notifyDataSetChanged()
        stripAdapter.notifyDataSetChanged()
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
