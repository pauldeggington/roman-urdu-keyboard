package com.paul.urdukb.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.paul.urdukb.BuildConfig
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.paul.urdukb.LlmEngine
import com.paul.urdukb.RomanUrdu

/**
 * The keyboard itself: puts the toolbar, keys and emoji screen on screen and turns taps into text.
 * Written from scratch for this app. The visual measurements come from Gboard.
 */
class UrduImeService : InputMethodService(), KeyboardView.Listener, ToolbarView.Listener, EmojiPanel.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var frame: FrameLayout
    private lateinit var container: LinearLayout
    private lateinit var typing: LinearLayout
    private lateinit var keyboard: KeyboardView
    private lateinit var toolbar: ToolbarView
    private lateinit var emoji: EmojiPanel
    private lateinit var clipboardPanel: ClipboardPanel
    private lateinit var popup: PopupLayer
    private lateinit var spacer: View

    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var viewReady = false
    private var emojiOpen = false
    private var clipOpen = false

    // glide typing
    private val glideThread = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** The word the last glide typed, so a suggestion can swap it and backspace can remove all of it. */
    private class GlideCommit(val words: List<String>, var chosen: Int, val prefix: String, val capMode: Int) {
        fun shown() = GlideCase.apply(words[chosen], capMode)
        fun tail() = prefix + shown() + " "
    }
    private var glideCommit: GlideCommit? = null
    private var inputSession = 0      // changes whenever a text field gets focus, so late results can be dropped

    // word suggestions while typing
    private enum class Strip { NONE, GLIDE, TYPING }
    private var strip = Strip.NONE
    private var typingCandidates: List<String> = emptyList()
    private var typingWord = ""                 // the word under the cursor, as it is written in the field
    private val wordBuf = StringBuilder()       // letters typed since the last space, for learning words
    private var suggestOn = true
    private var suggestionsAllowed = true
    private var learningAllowed = true
    private var suggestSeq = 0
    private val suggestThread = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val suggestRunnable = Runnable { computeSuggestions() }
    private var typedChars = 0
    private val hideSuggestions = Runnable { clearGlide() }
    private var clipListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private var searching = false
    private var baseEnterKind = KeyboardView.ENTER_RETURN

    // only a changed setting redraws the keyboard; saving recent emoji, clips or a key must not
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in Kb.LIVE_KEYS) handler.post { refreshAll() }
    }
    private val lookListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { refreshAll() } }
    private val deleteRepeat = object : Runnable {
        override fun run() {
            deleteOne()
            handler.postDelayed(this, 45)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Kb.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(lookListener)
        if (BuildConfig.PREVIEW) registerPreviewHooks()
        // glide typing: the word list is normally already loaded by the app's start-up; make sure it is
        Thread({ GlideDict.load(this) }, "glide-dict").start()
        // clipboard history: remember what is copied while the keyboard is running
        getSystemService(android.content.ClipboardManager::class.java)?.let { cm ->
            clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
                try {
                    val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
                    val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
                    if (!sensitive && clip.itemCount > 0) {
                        clip.getItemAt(0).coerceToText(this)?.toString()?.let { ClipStore.add(this, it) }
                    }
                } catch (_: Exception) {
                }
            }
            cm.addPrimaryClipChangedListener(clipListener)
        }
    }

    /** Only in emulator preview builds: lets CI switch screens and read key positions to take screenshots. */
    private fun registerPreviewHooks() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                handler.post {
                    if (!viewReady) return@post
                    when (intent.getStringExtra("do")) {
                        "letters" -> { showEmoji(false); keyboard.page = Page.LETTERS }
                        "symbols1" -> { showEmoji(false); keyboard.page = Page.SYMBOLS1 }
                        "symbols2" -> { showEmoji(false); keyboard.page = Page.SYMBOLS2 }
                        "emoji" -> { showEmoji(true); emoji.showMode("emoji") }
                        "emoticons" -> { showEmoji(true); emoji.showMode("emoticon") }
                        "clipboard" -> { ClipStore.seedForPreview(this@UrduImeService); showClipboard(true) }
                        "clipboard_off" -> { ClipStore.setEnabled(this@UrduImeService, false); showClipboard(true) }
                        "gif" -> { showEmoji(true); emoji.showMode("gif") }
                        "stickers" -> { showEmoji(true); emoji.showMode("sticker") }
                        "search_emoji" -> { showEmoji(true); emoji.showMode("emoji"); emoji.openSearchFromHook(); emoji.searchType("heart") }
                        "search_gif" -> { showEmoji(true); emoji.showMode("gif"); emoji.openSearchFromHook(); emoji.searchType("thanks") }
                        "tools" -> toolbar.toolsOpen = true
                        "notools" -> toolbar.toolsOpen = false
                        "shift" -> keyboard.shift = KeyboardView.SHIFT_ON
                        "dump" -> {
                            keyboard.keyCenters().forEach { Log.i("PREVIEW", "KEY ${it.first} ${it.second.toInt()} ${it.third.toInt()}") }
                            val loc = IntArray(2)
                            toolbar.getLocationOnScreen(loc)
                            Log.i("PREVIEW", "TOOLBAR ${loc[0]} ${loc[1]} ${toolbar.width} ${toolbar.height}")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("com.paul.urdukb.PREVIEW")
        androidx.core.content.ContextCompat.registerReceiver(this, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        clipListener?.let { getSystemService(android.content.ClipboardManager::class.java)?.removePrimaryClipChangedListener(it) }
        Kb.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(lookListener)
        RomanUrdu.onStateChanged = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    // ------------------------------------------------------------------ views

    override fun onCreateInputView(): View {
        val d = resources.displayMetrics.density
        window?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            isNavigationBarContrastEnforced = false
        }
        popup = PopupLayer(this)
        keyboard = KeyboardView(this).apply { listener = this@UrduImeService; popupLayer = popup }
        toolbar = ToolbarView(this).apply { listener = this@UrduImeService }
        emoji = EmojiPanel(this).apply { listener = this@UrduImeService; visibility = View.GONE }
        clipboardPanel = ClipboardPanel(this).apply {
            listener = object : ClipboardPanel.Listener {
                override fun onClip(text: String) {
                    clearGlide()
                    RomanUrdu.onOtherKey(this@UrduImeService)
                    currentInputConnection?.commitText(text, 1)
                    lastSpace = 0
                    showClipboard(false)
                }
                override fun onBack() = showClipboard(false)
                override fun onDeleteDown() { deleteOne(); handler.postDelayed(deleteRepeat, 400) }
                override fun onDeleteUp() { handler.removeCallbacks(deleteRepeat) }
                override fun onFeedback() = feedback()
            }
            visibility = View.GONE
        }

        typing = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(keyboard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // the emoji screen sits above the keys, so while searching its results are right above the letters
            addView(emoji, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(clipboardPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(typing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // transparent room above the keys, where the key preview and the long-press popup are drawn
        spacer = View(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(spacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (EXTRA_TOP_DP * d).toInt()))
            addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        frame = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (BuildConfig.PREVIEW) Log.i("PREVIEW", "FRAMETOUCH ${ev.actionMasked} ${ev.x.toInt()} ${ev.y.toInt()}")
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(popup, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // draw the keyboard colour behind the navigation bar too, like Gboard
            setOnApplyWindowInsetsListener { _, insets ->
                applyBottomPadding(insets)
                insets
            }
        }
        // some apps never send the insets to the keyboard window, which left the bottom row under the system's
        // keyboard-switcher and hide-keyboard buttons: start with the usual height and correct it when insets arrive
        applyBottomPadding(null)
        frame.requestApplyInsets()
        RomanUrdu.onStateChanged = { if (viewReady) refreshToolbar() }
        viewReady = true
        // these are new views that have no colours yet, even if the old ones had them: apply them for sure
        appliedColors = null
        refreshAll()
        return frame
    }

    /**
     * Room under the bottom row: 36 dp (like Gboard) plus the navigation bar, where Android draws the globe and
     * hide-keyboard buttons. When the window reports no navigation bar height, use the usual one for the phone's
     * navigation mode (gesture bar 24 dp, buttons 48 dp) so the keys never end up under those buttons.
     */
    private fun applyBottomPadding(insets: WindowInsets?) {
        if (!viewReady && !::container.isInitialized) return
        val d = resources.displayMetrics.density
        val reported = insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        val mode = runCatching { android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 2) }.getOrDefault(2)
        val fallback = ((if (mode == 2) 24f else 48f) * d).toInt()
        container.setPadding(0, 0, 0, (if (reported > 0) reported else fallback) + (36f * d).toInt())
    }

    private var appliedColors: KbColors? = null
    private var vibrateOn = true
    private var soundOn = false
    private var autoCapOn = true
    private var doubleSpaceOn = true
    private var warmOn = true
    private val vibrator by lazy { getSystemService(VibratorManager::class.java)?.defaultVibrator }
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    private fun refreshAll() {
        if (!viewReady) return
        val colors = KbColors.of(this)
        // handing the colours to every panel redraws them all, so it only happens when they really changed
        if (colors != appliedColors) {
            appliedColors = colors
            container.setBackgroundColor(colors.bg)
            keyboard.colors = colors
            toolbar.colors = colors
            emoji.colors = colors
            clipboardPanel.colors = colors
            popup.colors = colors
        }
        // the keyboard window is see-through, so the background must always be painted (an app that rebuilt the
        // keyboard's views once left it transparent when this only ran after a colour change)
        container.setBackgroundColor(colors.bg)
        keyboard.showHints = Kb.hints(this)
        keyboard.showPreview = Kb.preview(this)
        keyboard.glideEnabled = Kb.glide(this)
        // read once here instead of on every key press
        vibrateOn = Kb.vibrate(this)
        soundOn = Kb.sound(this)
        autoCapOn = Kb.autoCap(this)
        doubleSpaceOn = Kb.doubleSpace(this)
        warmOn = Kb.warm(this)
        suggestOn = Kb.suggest(this)
        if (!suggestOn) clearTyping()
        refreshToolbar()
    }

    private fun refreshToolbar() {
        toolbar.toneLabel = RomanUrdu.toneLabel(this)
        toolbar.directionLabel = RomanUrdu.directionShort(this)
        toolbar.busy = RomanUrdu.busy
        toolbar.canRevert = RomanUrdu.canRevert
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAll()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!viewReady) return
        val top = spacer.height
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, frame.width, frame.height)
        if (BuildConfig.PREVIEW) Log.i("PREVIEW", "INSETS top=$top w=${frame.width} h=${frame.height}")
    }

    // ------------------------------------------------------------------ input session

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!viewReady) return
        inputSession++
        typedChars = 0
        clearGlide()
        clearTyping()
        wordBuf.setLength(0)
        // no suggestions or learning in password, email or address fields, or where the app asks for none
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        suggestionsAllowed = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_PASSWORD && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS && variation != InputType.TYPE_TEXT_VARIATION_URI &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        learningAllowed = suggestionsAllowed && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        frame.post { applyBottomPadding(frame.rootWindowInsets); frame.requestApplyInsets() }
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        keyboard.page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME)
            Page.SYMBOLS1 else Page.LETTERS
        keyboard.shift = KeyboardView.SHIFT_OFF
        toolbar.toolsOpen = false
        showEmoji(false)
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        baseEnterKind = if (noEnterAction) KeyboardView.ENTER_RETURN else when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> KeyboardView.ENTER_GO
            EditorInfo.IME_ACTION_SEARCH -> KeyboardView.ENTER_SEARCH
            EditorInfo.IME_ACTION_SEND -> KeyboardView.ENTER_SEND
            EditorInfo.IME_ACTION_DONE -> KeyboardView.ENTER_DONE
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> KeyboardView.ENTER_NEXT
            else -> KeyboardView.ENTER_RETURN
        }
        keyboard.enterKind = baseEnterKind
        refreshAll()
        updateShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        handler.removeCallbacks(deleteRepeat)
        clearTyping()
        PersonalWords.save(this)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (viewReady) {
            glideCommit?.let { g ->
                val tail = g.tail()
                if (currentInputConnection?.getTextBeforeCursor(tail.length, 0)?.toString() != tail) clearGlide()
            }
            updateShift()
            scheduleSuggest()
        }
    }

    // ------------------------------------------------------------------ shift and capitals

    private fun updateShift() {
        if (!viewReady || keyboard.shift == KeyboardView.SHIFT_CAPS || searching) return
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        if (ic == null || info == null || !autoCapOn || (info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            keyboard.shift = KeyboardView.SHIFT_OFF
            return
        }
        keyboard.shift = if (ic.getCursorCapsMode(info.inputType) != 0) KeyboardView.SHIFT_ON else KeyboardView.SHIFT_OFF
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        keyboard.shift = when {
            keyboard.shift == KeyboardView.SHIFT_CAPS -> KeyboardView.SHIFT_OFF
            keyboard.shift == KeyboardView.SHIFT_ON && now - lastShiftTap < 350 -> KeyboardView.SHIFT_CAPS
            keyboard.shift == KeyboardView.SHIFT_ON -> KeyboardView.SHIFT_OFF
            else -> KeyboardView.SHIFT_ON
        }
        lastShiftTap = now
    }

    // ------------------------------------------------------------------ keys

    override fun onKey(key: KeyDef, text: String?) {
        if (BuildConfig.PREVIEW) Log.i("PREVIEW", "ONKEY ${key.type} $text ic=${currentInputConnection != null}")
        RomanUrdu.onOtherKey(this)
        if (key.type != KeyType.DELETE) clearGlide()
        when (key.type) {
            KeyType.CHAR -> typeText(text ?: key.label)
            KeyType.SHIFT -> toggleShift()
            KeyType.DELETE -> deleteOne()
            KeyType.SYMBOLS -> keyboard.page = key.target ?: Page.LETTERS
            KeyType.EMOJI -> showEmoji(true)
            KeyType.SPACE -> typeSpace()
            KeyType.ENTER -> pressEnter()
            KeyType.SPACER -> {}
        }
    }

    private fun typeText(s: String) {
        if (searching) {
            emoji.searchType(s)
            if (keyboard.shift == KeyboardView.SHIFT_ON) keyboard.shift = KeyboardView.SHIFT_OFF
            return
        }
        val ic = currentInputConnection ?: return
        trackWord(s)
        ic.commitText(s, 1)
        noteTyped(s.length)
        lastSpace = 0
        if (keyboard.shift == KeyboardView.SHIFT_ON) keyboard.shift = KeyboardView.SHIFT_OFF
        updateShift()
    }

    /** After a dozen typed characters the translator is loaded in the background, so Translate does not make you wait. */
    private fun noteTyped(n: Int) {
        if (typedChars >= WARM_AFTER) return
        typedChars += n
        if (typedChars >= WARM_AFTER && warmOn) LlmEngine.warmUp(applicationContext)
    }

    private fun typeSpace() {
        if (searching) { emoji.searchType(" "); return }
        val ic = currentInputConnection ?: return
        trackWord(" ")
        clearTyping()
        val now = SystemClock.uptimeMillis()
        if (doubleSpaceOn && now - lastSpace < 500) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpace = 0
                updateShift()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
        updateShift()
    }

    private fun deleteOne() {
        if (searching) { emoji.searchBackspace(); return }
        val ic = currentInputConnection ?: return
        if (wordBuf.isNotEmpty()) wordBuf.setLength(wordBuf.length - 1)
        glideCommit?.let { g ->
            // backspace right after a glide removes the whole word, like Gboard
            val tail = g.tail()
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                ic.deleteSurroundingText(tail.length, 0)
                clearGlide()
                lastSpace = 0
                updateShift()
                return
            }
            clearGlide()
        }
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        lastSpace = 0
        updateShift()
        scheduleSuggest()
    }

    private fun pressEnter() {
        if (searching) { emoji.searchSubmit(); return }
        val ic = currentInputConnection ?: return
        trackWord("\n")
        clearTyping()
        val info = currentInputEditorInfo
        val options = info?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        when {
            info != null && info.actionLabel != null && info.actionId != 0 -> ic.performEditorAction(info.actionId)
            options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
                action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED ->
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            else -> ic.performEditorAction(action)
        }
        lastSpace = 0
        updateShift()
    }

    override fun onCursorMove(delta: Int) {
        if (searching) return
        sendDownUpKeyEvents(if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onFeedback(key: KeyDef) = feedback(key.type)

    private fun feedback(type: KeyType? = null) {
        if (vibrateOn) {
            runCatching { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) }
        }
        if (soundOn) {
            val fx = when (type) {
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, -1f)
        }
    }

    // ------------------------------------------------------------------ glide typing

    override fun onGlide(path: List<GlidePoint>) {
        val decoder = GlideDict.decoder
        val layout = keyboard.glideLayout()
        if (decoder == null || layout == null) return
        val points = ArrayList(path)
        val session = inputSession
        glideThread.execute {
            val words = decoder.decode(points, layout, 5)
            if (BuildConfig.PREVIEW) Log.i("PREVIEW", "GLIDE $words")
            // the person may have moved to another field while this was working: then the word is not wanted there
            handler.post { if (words.isNotEmpty() && session == inputSession) commitGlide(words) }
        }
    }

    private fun commitGlide(words: List<String>) {
        val ic = currentInputConnection ?: return
        if (searching) { emoji.searchType(GlideCase.apply(words[0], KeyboardView.SHIFT_OFF) + " "); return }
        RomanUrdu.onOtherKey(this)
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val prefix = if (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'\u201C\u2018") " " else ""
        val g = GlideCommit(words, 0, prefix, keyboard.shift)
        ic.commitText(g.tail(), 1)
        noteTyped(words[0].length + 1)
        glideCommit = g
        lastSpace = 0
        if (keyboard.shift == KeyboardView.SHIFT_ON) keyboard.shift = KeyboardView.SHIFT_OFF
        showSuggestions()
        updateShift()
    }

    private fun showSuggestions() {
        val g = glideCommit ?: return
        strip = Strip.GLIDE
        toolbar.suggestions = g.words.take(3).map { GlideCase.apply(it, g.capMode) }
        toolbar.suggestionIndex = g.chosen
        handler.removeCallbacks(hideSuggestions)
        handler.postDelayed(hideSuggestions, 2500)   // then Translate, Casual and Auto come back
    }

    /** A suggestion was tapped: swap the word that was typed for it. */
    private fun chooseGlide(i: Int) {
        val g = glideCommit ?: return
        if (i !in 0 until minOf(3, g.words.size) || i == g.chosen) return
        val ic = currentInputConnection ?: return
        val old = g.tail()
        if (ic.getTextBeforeCursor(old.length, 0)?.toString() != old) { clearGlide(); return }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(old.length, 0)
        g.chosen = i
        ic.commitText(g.tail(), 1)
        ic.endBatchEdit()
        showSuggestions()
    }

    private fun clearGlide() {
        glideCommit = null
        handler.removeCallbacks(hideSuggestions)
        if (viewReady && strip == Strip.GLIDE) { strip = Strip.NONE; toolbar.suggestions = emptyList() }
    }

    // ------------------------------------------------------------------ word suggestions

    /** Keeps the word being typed, and learns it when it is finished (a space or punctuation). */
    private fun trackWord(typed: String) {
        for (ch in typed) {
            if (ch.isLetter() || ch == '\'') wordBuf.append(ch)
            else {
                val w = wordBuf.toString()
                wordBuf.setLength(0)
                // only words the built-in list does not know (names, slang, Roman Urdu) are remembered
                if (learningAllowed && w.length >= 3 && GlideDict.suggester?.isWord(w) == false) PersonalWords.learn(this, w)
            }
        }
        if (wordBuf.length > 40) wordBuf.setLength(0)
    }

    /** Looks at the word under the cursor a moment after typing stops changing it (also off the main thread). */
    private fun scheduleSuggest() {
        handler.removeCallbacks(suggestRunnable)
        if (!suggestOn || !suggestionsAllowed || searching || emojiOpen || clipOpen) { clearTyping(); return }
        handler.postDelayed(suggestRunnable, 30)
    }

    private fun computeSuggestions() {
        val ic = currentInputConnection ?: return
        val suggester = GlideDict.suggester ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        // only when the cursor is at the end of a word
        if (after.isNotEmpty() && (after[0].isLetter() || after[0] == '\'')) { clearTyping(); return }
        var start = before.length
        while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'')) start--
        val word = before.substring(start).trimStart('\'')
        if (word.length < 2) { clearTyping(); return }
        val seq = ++suggestSeq
        val app = applicationContext
        suggestThread.execute {
            val found = suggester.suggest(word, PersonalWords.snapshot(app), 3)
            handler.post { if (seq == suggestSeq) showTyping(word, found) }
        }
    }

    private fun showTyping(word: String, found: List<String>) {
        if (!viewReady || strip == Strip.GLIDE) return
        if (found.isEmpty()) { clearTyping(); return }
        typingWord = word
        typingCandidates = found.map { WordSuggester.matchCase(word, it) }
        strip = Strip.TYPING
        toolbar.suggestions = typingCandidates
        toolbar.suggestionIndex = -1
    }

    private fun clearTyping() {
        suggestSeq++
        handler.removeCallbacks(suggestRunnable)
        typingCandidates = emptyList()
        typingWord = ""
        if (viewReady && strip == Strip.TYPING) { strip = Strip.NONE; toolbar.suggestions = emptyList() }
    }

    /** A suggested word was tapped: it replaces the word being typed, and a space follows. */
    private fun chooseTyping(i: Int) {
        val word = typingCandidates.getOrNull(i) ?: return
        val ic = currentInputConnection ?: return
        val typed = typingWord
        if (typed.isEmpty() || ic.getTextBeforeCursor(typed.length, 0)?.toString() != typed) { clearTyping(); return }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        wordBuf.setLength(0)
        lastSpace = 0
        clearTyping()
        updateShift()
    }

    // ------------------------------------------------------------------ toolbar

    override fun onTool(tool: ToolbarView.Tool) {
        feedback()
        val ic = currentInputConnection
        when (tool) {
            ToolbarView.Tool.SUGGESTION_1 -> if (strip == Strip.TYPING) chooseTyping(0) else chooseGlide(0)
            ToolbarView.Tool.SUGGESTION_2 -> if (strip == Strip.TYPING) chooseTyping(1) else chooseGlide(1)
            ToolbarView.Tool.SUGGESTION_3 -> if (strip == Strip.TYPING) chooseTyping(2) else chooseGlide(2)
            ToolbarView.Tool.GRID -> toolbar.toolsOpen = !toolbar.toolsOpen
            ToolbarView.Tool.TRANSLATE -> RomanUrdu.translateOrRevert(this) { currentInputConnection?.finishComposingText() }
            ToolbarView.Tool.TONE -> RomanUrdu.toggleTone(this)
            ToolbarView.Tool.DIRECTION -> RomanUrdu.cycleDirection(this)
            ToolbarView.Tool.MIC -> startVoice()
            ToolbarView.Tool.EMOJI -> showEmoji(true)
            ToolbarView.Tool.SELECT_ALL -> ic?.performContextMenuAction(android.R.id.selectAll)
            ToolbarView.Tool.COPY -> ic?.performContextMenuAction(android.R.id.copy)
            ToolbarView.Tool.CUT -> ic?.performContextMenuAction(android.R.id.cut)
            ToolbarView.Tool.CLIPBOARD -> showClipboard(true)
            ToolbarView.Tool.SETTINGS -> RomanUrdu.openApp(this, null)
        }
    }

    /**
     * Voice typing: hands over to the phone's voice typing keyboard (Google's), which switches back here when
     * you stop talking. The app itself never touches the microphone, so it needs no microphone permission.
     */
    private fun startVoice() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        for (info in imm.enabledInputMethodList) {
            if (info.packageName == packageName) continue
            for (st in imm.getEnabledInputMethodSubtypeList(info, true)) {
                if (st.mode.equals("voice", ignoreCase = true)) {
                    try {
                        switchInputMethod(info.id, st)
                        return
                    } catch (_: Exception) {
                    }
                }
            }
        }
        Toast.makeText(
            this,
            "Voice typing isn't turned on. In Settings, go to System > Keyboard > On-screen keyboard and turn on Google voice typing.",
            Toast.LENGTH_LONG,
        ).show()
    }

    // ------------------------------------------------------------------ emoji

    private fun showEmoji(on: Boolean) {
        if (!viewReady) return
        if (!on && searching) emoji.closeSearch()
        clipOpen = false
        emojiOpen = on
        if (on) emoji.refresh()
        layoutPanels()
        popup.hide()
    }

    private fun showClipboard(on: Boolean) {
        if (!viewReady) return
        if (on && searching) emoji.closeSearch()
        emojiOpen = false
        clipOpen = on
        if (on) clipboardPanel.refresh()
        layoutPanels()
        popup.hide()
    }

    /** The emoji screen replaces the keys, except while searching: then it sits above the letters. */
    private fun layoutPanels() {
        typing.visibility = if ((!emojiOpen && !clipOpen) || searching) View.VISIBLE else View.GONE
        toolbar.visibility = if (searching) View.GONE else View.VISIBLE
        emoji.visibility = if (emojiOpen) View.VISIBLE else View.GONE
        clipboardPanel.visibility = if (clipOpen) View.VISIBLE else View.GONE
        keyboard.enterKind = if (searching) KeyboardView.ENTER_SEARCH else baseEnterKind
        frame.requestLayout()
    }

    override fun onSearchState(active: Boolean) {
        searching = active
        if (active) {
            keyboard.page = Page.LETTERS
            keyboard.shift = KeyboardView.SHIFT_OFF
        }
        layoutPanels()
        if (!active) updateShift()
    }

    /** A GIF or sticker: sent as an image where the app accepts one, otherwise as a link. */
    override fun onMedia(item: GifItem) {
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        if (info == null || ic == null) return
        clearGlide()
        RomanUrdu.onOtherKey(this)
        val accepts = androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(info)
            .any { android.content.ClipDescription.compareMimeTypes("image/gif", it) }
        if (!accepts) {
            ic.commitText(item.pageUrl + " ", 1)
            Toast.makeText(this, "This app can't take GIFs from a keyboard, so the link was typed instead.", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val file = MediaCache.download(this, item)
            handler.post {
                val ic2 = currentInputConnection
                val info2 = currentInputEditorInfo
                var sent = false
                if (file != null && ic2 != null && info2 != null) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    val content = androidx.core.view.inputmethod.InputContentInfoCompat(
                        uri, android.content.ClipDescription(item.title.ifEmpty { "GIF" }, arrayOf("image/gif")), android.net.Uri.parse(item.pageUrl),
                    )
                    sent = androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                        ic2, info2, content, androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
                    )
                }
                if (!sent) {
                    ic2?.commitText(item.pageUrl + " ", 1)
                    Toast.makeText(this, "Couldn't send the picture, so the link was typed instead.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onOpenApp() {
        showEmoji(false)
        RomanUrdu.openApp(this, "giphy")
    }

    override fun onEmoji(text: String) {
        clearGlide()
        RomanUrdu.onOtherKey(this)
        currentInputConnection?.commitText(text, 1)
        lastSpace = 0
    }

    override fun onBack() = showEmoji(false)

    override fun onDeleteDown() {
        deleteOne()
        handler.postDelayed(deleteRepeat, 400)
    }

    override fun onDeleteUp() {
        handler.removeCallbacks(deleteRepeat)
    }

    override fun onFeedback() = feedback()

    companion object {
        const val EXTRA_TOP_DP = 64f
        private const val WARM_AFTER = 12
    }
}
