package com.paul.urdukb.ime

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.view.inputmethod.InputConnection
import com.paul.urdukb.Detect
import com.paul.urdukb.VariantMap
import java.util.concurrent.Executors

/** What [TypingAssist] needs from the keyboard that owns it. */
internal interface TypingHost {
    val context: Context
    val handler: Handler
    fun connection(): InputConnection?
    /** Shows these words in the strip above the keys (ignored while a glide result is showing). */
    fun showStrip(items: List<String>)
    fun clearStrip()
    /** Something was typed or replaced: the shift state and the "double space" timer need a refresh. */
    fun textChanged()
}

/**
 * The typing help: word suggestions, automatic correction (and its undo), next-word suggestions, the Paste chip,
 * and learning the words and word pairs the person types. It reads the text around the cursor, works out the
 * answer off the main thread, and hands the words to the strip through [TypingHost].
 */
internal class TypingAssist(private val host: TypingHost) {

    var suggestOn = true
    var autocorrectOn = true
    /** False in password, email and address fields, or where the app asks for no suggestions. */
    var suggestionsAllowed = true
    var learningAllowed = true
    /** True while the emoji search or a panel (emoji, clipboard) covers the keys: nothing is suggested then. */
    var blocked = false

    private class AutoFix(val original: String, val replacement: String, val delimiter: String)

    private val wordBuf = StringBuilder()            // letters typed since the last space, for learning words
    private var prevWord = ""                        // the word before the one being typed, for learning word pairs
    private var autoFix: AutoFix? = null             // the last automatic correction, so Backspace can undo it
    private var pasteOffer: String? = null           // what was just copied, offered as a Paste chip in an empty field
    private var pasteOfferAt = 0L
    private var pasteText: String? = null            // set while the strip shows that chip
    private var typingCandidates: List<String> = emptyList()
    private var typingWord = ""                      // the word under the cursor, as it is written in the field
    private var seq = 0
    private val thread = Executors.newSingleThreadExecutor()
    private val runnable = Runnable { compute() }

    private val ctx: Context get() = host.context

    // ---------------------------------------------------------------- what the keyboard calls

    fun resetWord() = wordBuf.setLength(0)

    fun newField() {
        resetWord()
        clear()
    }

    /** Before a character is committed: fixes a typo if this ends a word, otherwise keeps track of the word. */
    fun beforeText(ic: InputConnection, s: String) {
        val endsWord = !(s[0].isLetter() || s[0] == '\'')
        if (endsWord && s[0] != ' ' && maybeAutocorrect(ic, s)) { resetWord(); clear() } else trackWord(s)
    }

    fun beforeSpace(ic: InputConnection) {
        if (maybeAutocorrect(ic, " ")) resetWord() else trackWord(" ")
        clear()
    }

    fun beforeEnter() {
        trackWord("\n")
        clear()
    }

    /** Any key but Backspace: the last correction can no longer be undone. */
    fun forgetCorrection() { autoFix = null }

    /** Backspace right after an automatic correction puts back what was typed (and stops correcting that word). */
    fun undoCorrection(ic: InputConnection): Boolean {
        val f = autoFix ?: return false
        val tail = f.replacement + f.delimiter
        autoFix = null
        if (ic.getTextBeforeCursor(tail.length, 0)?.toString() != tail) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(tail.length, 0)
        ic.commitText(f.original, 1)
        ic.endBatchEdit()
        if (learningAllowed) { PersonalWords.learn(ctx, f.original); PersonalWords.learn(ctx, f.original) }
        resetWord(); wordBuf.append(f.original)
        host.textChanged()
        schedule()
        return true
    }

    /** Backspace takes a letter off the word being tracked. */
    fun deletedLetter() { if (wordBuf.isNotEmpty()) wordBuf.setLength(wordBuf.length - 1) }

    fun offerPaste(text: String) {
        pasteOffer = text.trim(); pasteOfferAt = SystemClock.uptimeMillis()
        host.handler.post { schedule() }
    }

    // ---------------------------------------------------------------- learning

    /** Keeps the word being typed, and learns it when it is finished (a space or punctuation). */
    private fun trackWord(typed: String) {
        for (ch in typed) {
            if (ch.isLetter() || ch == '\'') wordBuf.append(ch)
            else {
                val w = wordBuf.toString()
                wordBuf.setLength(0)
                if (w.isNotEmpty()) {
                    if (learningAllowed && prevWord.isNotEmpty()) PersonalWords.learnPair(ctx, prevWord, w)
                    prevWord = if (ch in ".!?\n") "" else w.lowercase()
                } else if (ch in ".!?\n") prevWord = ""
                // only words the built-in list does not know (names, slang, Roman Urdu) are remembered
                if (learningAllowed && w.length >= 3 && GlideDict.suggester?.isWord(w) == false) PersonalWords.learn(ctx, w)
            }
        }
        if (wordBuf.length > 40) wordBuf.setLength(0)
    }

    // ---------------------------------------------------------------- automatic correction

    /**
     * When a space or punctuation mark is about to be typed: replaces a clear typo with the right word.
     * Returns true if it changed something. Names, capitals-only words, Roman Urdu and words the person uses are left alone.
     */
    private fun maybeAutocorrect(ic: InputConnection, delimiter: String): Boolean {
        if (!autocorrectOn || !suggestOn || !suggestionsAllowed || blocked) return false
        val suggester = GlideDict.suggester ?: return false
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: return false
        var start = before.length
        while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'')) start--
        val word = before.substring(start)
        if (word.length < 2 || word[0] == '\'' || word.any { it == '\'' }) return false
        val lead = before.substring(0, start).trimEnd(' ')
        val sentenceStart = lead.isEmpty() || lead.last() in ".!?\n"
        if (word[0].isUpperCase() && !sentenceStart) return false          // probably a name
        if (word.length > 1 && word.all { it.isUpperCase() }) return false  // an abbreviation
        // this app is for Roman Urdu: when the text around looks like it, or the spelling is a known Roman Urdu one, hands off
        if (VariantMap.isKnownVariant(ctx, word.lowercase()) || Detect.guess(before) { VariantMap.isKnownVariant(ctx, it) } == Detect.Language.ROMAN_URDU) return false
        val fix = suggester.autocorrect(word, PersonalWords.snapshot(ctx)) ?: return false
        val replacement = WordSuggester.matchCase(word, fix)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(replacement, 1)
        ic.endBatchEdit()
        autoFix = AutoFix(word, replacement, delimiter)
        if (learningAllowed && prevWord.isNotEmpty()) PersonalWords.learnPair(ctx, prevWord, fix.lowercase())
        prevWord = if (delimiter[0] in ".!?\n") "" else fix.lowercase()
        return true
    }

    // ---------------------------------------------------------------- suggestions

    /** Looks at the word under the cursor a moment after typing stops changing it (also off the main thread). */
    fun schedule() {
        host.handler.removeCallbacks(runnable)
        if (!suggestOn || !suggestionsAllowed || blocked) { clear(); return }
        host.handler.postDelayed(runnable, 30)
    }

    private fun compute() {
        val ic = host.connection() ?: return
        val suggester = GlideDict.suggester ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        // only when the cursor is at the end of a word
        if (after.isNotEmpty() && (after[0].isLetter() || after[0] == '\'')) { clear(); return }
        // an empty field and something was copied a moment ago: offer to paste it
        val offer = pasteOffer
        if (before.isEmpty() && after.isEmpty() && offer != null && SystemClock.uptimeMillis() - pasteOfferAt < 120_000) {
            showPaste(offer); return
        }
        var start = before.length
        while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'')) start--
        val word = before.substring(start).trimStart('\'')
        if (word.isEmpty() && before.endsWith(" ")) { computeNextWord(before); return }
        if (word.length < 2) { clear(); return }
        val mine = ++seq
        val app = ctx.applicationContext
        thread.execute {
            val found = suggester.suggest(word, PersonalWords.snapshot(app), 3)
            host.handler.post { if (mine == seq) showTyping(word, found) }
        }
    }

    /** After a word and a space: offers what usually comes next. Not for Roman Urdu text, and not in an empty field. */
    private fun computeNextWord(before: String) {
        val trimmed = before.trimEnd(' ')
        if (trimmed.isEmpty()) { clear(); return }
        var start = trimmed.length
        while (start > 0 && (trimmed[start - 1].isLetter() || trimmed[start - 1] == '\'')) start--
        val prev = trimmed.substring(start).trimStart('\'').lowercase()
        val key = if (prev.isEmpty()) (if (trimmed.last() in ".!?") "<s>" else "") else prev
        if (key.isEmpty()) { clear(); return }
        val sentenceStart = key == "<s>"
        val mine = ++seq
        val app = ctx.applicationContext
        thread.execute {
            val roman = Detect.guess(before) { VariantMap.isKnownVariant(app, it) } == Detect.Language.ROMAN_URDU
            val found = if (roman) emptyList() else NextWord.predict(app, key).map {
                when {
                    it == "i" || it.startsWith("i'") -> "I" + it.drop(1)
                    sentenceStart -> it.replaceFirstChar { c -> c.uppercase() }
                    else -> it
                }
            }
            host.handler.post { if (mine == seq) showNext(found) }
        }
    }

    private fun showPaste(text: String) {
        pasteText = text
        typingWord = ""
        val oneLine = text.replace('\n', ' ')
        typingCandidates = listOf("📋 " + if (oneLine.length > 12) oneLine.take(11).trimEnd() + "…" else oneLine)
        host.showStrip(typingCandidates)
    }

    private fun showNext(found: List<String>) {
        if (found.isEmpty()) { clear(); return }
        typingWord = ""
        typingCandidates = found
        host.showStrip(found)
    }

    private fun showTyping(word: String, found: List<String>) {
        if (found.isEmpty()) { clear(); return }
        typingWord = word
        typingCandidates = found.map { WordSuggester.matchCase(word, it) }
        host.showStrip(typingCandidates)
    }

    fun clear() {
        seq++
        host.handler.removeCallbacks(runnable)
        typingCandidates = emptyList()
        typingWord = ""
        pasteText = null
        host.clearStrip()
    }

    /** A suggested word was tapped: it replaces the word being typed, and a space follows. */
    fun choose(i: Int) {
        val word = typingCandidates.getOrNull(i) ?: return
        val ic = host.connection() ?: return
        pasteText?.let { t ->
            ic.commitText(t, 1)
            pasteOffer = null
            clear()
            host.textChanged()
            return
        }
        val typed = typingWord
        if (typed.isEmpty()) {
            // a next-word suggestion: it goes in after the space that is already there
            if (ic.getTextBeforeCursor(1, 0)?.toString() != " ") { clear(); return }
            if (learningAllowed && prevWord.isNotEmpty()) PersonalWords.learnPair(ctx, prevWord, word)
            ic.commitText("$word ", 1)
            prevWord = word.lowercase()
            resetWord()
            host.textChanged()
            schedule()
            return
        }
        if (ic.getTextBeforeCursor(typed.length, 0)?.toString() != typed) { clear(); return }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        resetWord()
        prevWord = word.lowercase()
        clear()
        host.textChanged()
        schedule()
    }
}
