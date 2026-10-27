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
    private var clipListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private var searching = false
    private var baseEnterKind = KeyboardView.ENTER_RETURN

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { refreshAll() } }
    private val deleteRepeat = object : Runnable {
        override fun run() {
            deleteOne()
            handler.postDelayed(this, 45)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Kb.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        if (BuildConfig.PREVIEW) registerPreviewHooks()
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
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        clipListener?.let { getSystemService(android.content.ClipboardManager::class.java)?.removePrimaryClipChangedListener(it) }
        Kb.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        applicationContext.getSharedPreferences("look", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
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
                val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                // Gboard leaves 36 dp under the bottom row, above the navigation bar
                container.setPadding(0, 0, 0, nav + (36f * resources.displayMetrics.density).toInt())
                insets
            }
        }
        RomanUrdu.onStateChanged = { if (viewReady) refreshToolbar() }
        viewReady = true
        refreshAll()
        return frame
    }

    private fun refreshAll() {
        if (!viewReady) return
        val colors = KbColors.of(this)
        container.setBackgroundColor(colors.bg)
        keyboard.colors = colors
        toolbar.colors = colors
        emoji.colors = colors
        clipboardPanel.colors = colors
        popup.colors = colors
        keyboard.showHints = Kb.hints(this)
        keyboard.showPreview = Kb.preview(this)
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
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (viewReady) updateShift()
    }

    // ------------------------------------------------------------------ shift and capitals

    private fun updateShift() {
        if (!viewReady || keyboard.shift == KeyboardView.SHIFT_CAPS || searching) return
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        if (ic == null || info == null || !Kb.autoCap(this) || (info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
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
        ic.commitText(s, 1)
        lastSpace = 0
        if (keyboard.shift == KeyboardView.SHIFT_ON) keyboard.shift = KeyboardView.SHIFT_OFF
        updateShift()
    }

    private fun typeSpace() {
        if (searching) { emoji.searchType(" "); return }
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (Kb.doubleSpace(this) && now - lastSpace < 500) {
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
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        lastSpace = 0
        updateShift()
    }

    private fun pressEnter() {
        if (searching) { emoji.searchSubmit(); return }
        val ic = currentInputConnection ?: return
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
        if (Kb.vibrate(this)) {
            runCatching {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
                    ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        }
        if (Kb.sound(this)) {
            val fx = when (type) {
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            getSystemService(AudioManager::class.java)?.playSoundEffect(fx, -1f)
        }
    }

    // ------------------------------------------------------------------ toolbar

    override fun onTool(tool: ToolbarView.Tool) {
        feedback()
        val ic = currentInputConnection
        when (tool) {
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
    }
}
