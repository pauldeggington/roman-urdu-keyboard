package com.paul.urdukb

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A Gboard-style keyboard drawn on one canvas: toolbar strip on top, then five rows of keys.
 * Handles long-press alternates, shift / caps lock, hold-to-repeat backspace and space-bar cursor drag.
 */
class KeyboardView(context: Context, private val host: Host) : View(context) {

    interface Host {
        fun commitText(text: String)
        fun backspace()
        fun deleteWord()
        fun enter()
        fun moveCursor(delta: Int)
        fun translate()
        fun undo()
        fun paste()
        fun switchKeyboard()
        fun showPicker()
        fun dismissError()
    }

    enum class Kind { CHAR, SHIFT, BACKSPACE, SYMBOLS, SYMBOLS2, LETTERS, ENTER, GLOBE, SPACE, GAP, CHIP_TRANSLATE, CHIP_UNDO, CHIP_PASTE }
    enum class Shift { OFF, ON, LOCK }
    enum class Page { LETTERS, SYM1, SYM2 }

    private class Key(val kind: Kind, val label: String, val weight: Float, val alts: String = "") {
        val rect = RectF()
    }

    // ---- state ----
    private var pal = Palette(context)
    private var page = Page.LETTERS
    private var shift = Shift.OFF
    private var lastShiftTap = 0L
    private var translating = false
    private var canUndo = false
    private var errorText: String? = null

    private var rows: List<List<Key>> = emptyList()
    private val stripKeys = listOf(
        Key(Kind.CHIP_TRANSLATE, "Translate", 0f),
        Key(Kind.CHIP_UNDO, "Undo", 0f),
        Key(Kind.CHIP_PASTE, "Paste", 0f),
    )

    // ---- touch state ----
    private var activeId = -1
    private var pressedKey: Key? = null
    private var downX = 0f
    private var lastSpaceX = 0f
    private var altOpen = false
    private var altKeys: List<String> = emptyList()
    private var altIndex = 0
    private var longFired = false
    private var spaceDragging = false
    private var repeating = false
    private var repeatCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable { onLongPress() }
    private val backspaceTick = object : Runnable {
        override fun run() {
            repeatCount++
            if (repeatCount > 12) host.deleteWord() else host.backspace()
            handler.postDelayed(this, 45)
        }
    }

    // ---- drawing helpers ----
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val errP = Paint(Paint.ANTI_ALIAS_FLAG)

    private val stripH get() = dp(46f)
    private val keysH get() = dp(250f)
    private val bottomPad get() = dp(6f)

    // ---- public API for the service ----
    fun showNumbers(on: Boolean) {
        page = if (on) Page.SYM1 else Page.LETTERS
        layoutKeys()
        invalidate()
    }

    fun setAutoCaps(on: Boolean) {
        if (shift == Shift.LOCK) return
        val n = if (on) Shift.ON else Shift.OFF
        if (n != shift) {
            shift = n
            invalidate()
        }
    }

    fun setTranslating(v: Boolean) { translating = v; invalidate() }
    fun setCanUndo(v: Boolean) { canUndo = v; invalidate() }
    fun setError(s: String?) { errorText = s; invalidate() }
    fun refreshTheme() { pal = Palette(context); invalidate() }

    // ---- layout ----
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            (stripH + keysH + bottomPad).toInt(),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(backspaceTick)
        super.onDetachedFromWindow()
    }

    private fun c(s: String, w: Float = 1f) = Key(Kind.CHAR, s, w, ALTS[s].orEmpty())
    private fun chars(s: String) = s.map { c(it.toString()) }

    private fun buildRows(): List<List<Key>> {
        val num = chars("1234567890")
        val letters = page == Page.LETTERS
        val bottom = listOf(
            Key(if (letters) Kind.SYMBOLS else Kind.LETTERS, if (letters) "?123" else "ABC", 1.5f),
            Key(Kind.GLOBE, "", 1f),
            c(","),
            Key(Kind.SPACE, "", 3.5f),
            c("."),
            Key(Kind.ENTER, "", 2f),
        )
        return when (page) {
            Page.LETTERS -> listOf(
                num,
                chars("qwertyuiop"),
                listOf(Key(Kind.GAP, "", 0.5f)) + chars("asdfghjkl") + Key(Kind.GAP, "", 0.5f),
                listOf(Key(Kind.SHIFT, "", 1.5f)) + chars("zxcvbnm") + Key(Kind.BACKSPACE, "", 1.5f),
                bottom,
            )
            Page.SYM1 -> listOf(
                num,
                chars("@#\$_&-+()/"),
                listOf(Key(Kind.SYMBOLS2, "=\\<", 1.5f)) + chars("*\"':;!?") + Key(Kind.BACKSPACE, "", 1.5f),
                bottom,
            )
            Page.SYM2 -> listOf(
                num,
                chars("~`|•√π÷×¶∆"),
                listOf(Key(Kind.SYMBOLS, "?123", 1.5f)) + chars("£¢€¥^°=") + Key(Kind.BACKSPACE, "", 1.5f),
                bottom,
            )
        }
    }

    private fun layoutKeys() {
        if (width == 0 || height == 0) return
        rows = buildRows()
        val padX = dp(3f)
        val top = stripH
        val rowH = (height - stripH - bottomPad) / rows.size
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (width - 2 * padX) / total
            var x = padX
            for (k in row) {
                val w = k.weight * unit
                k.rect.set(x, top + r * rowH, x + w, top + (r + 1) * rowH)
                x += w
            }
        }
        val chipH = dp(34f)
        val chipTop = (stripH - chipH) / 2
        val widths = floatArrayOf(dp(124f), dp(78f), dp(84f))
        var x = dp(10f)
        stripKeys.forEachIndexed { i, k ->
            k.rect.set(x, chipTop, x + widths[i], chipTop + chipH)
            x += widths[i] + dp(8f)
        }
    }

    private fun shown(k: Key): String =
        if (page == Page.LETTERS && shift != Shift.OFF && k.label.length == 1 && k.label[0].isLetter()) k.label.uppercase()
        else k.label

    private fun cased(s: String): String =
        if (page == Page.LETTERS && shift != Shift.OFF) s.uppercase() else s

    // ---- drawing ----
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(pal.kbBg)
        drawStrip(canvas)
        for (row in rows) for (k in row) if (k.kind != Kind.GAP) drawKey(canvas, k)
        drawPreview(canvas)
        drawAlts(canvas)
    }

    private fun drawStrip(c: Canvas) {
        val err = errorText
        if (err != null) {
            errP.textSize = sp(12f)
            errP.color = pal.error
            val avail = width - dp(28f)
            val t = TextUtils.ellipsize(err, android.text.TextPaint(errP), avail, TextUtils.TruncateAt.END).toString()
            val fm = errP.fontMetrics
            c.drawText(t, dp(14f), stripH / 2 - (fm.ascent + fm.descent) / 2, errP)
            return
        }
        for (k in stripKeys) {
            val pressed = k === pressedKey
            val enabled = when (k.kind) {
                Kind.CHIP_TRANSLATE -> !translating
                Kind.CHIP_UNDO -> canUndo
                else -> true
            }
            val primary = k.kind == Kind.CHIP_TRANSLATE
            fill.color = when {
                primary -> if (pressed) pal.accentPressed else pal.accent
                else -> if (pressed) pal.keyPressed else pal.key
            }
            fill.alpha = if (enabled) 255 else 120
            c.drawRoundRect(k.rect, k.rect.height() / 2, k.rect.height() / 2, fill)
            val label = if (k.kind == Kind.CHIP_TRANSLATE && translating) "Translating…" else k.label
            val fg = if (primary) pal.onAccent else pal.kbText
            drawCentered(c, label, k.rect.centerX(), k.rect.centerY(), sp(14f), fg, if (enabled) 255 else 120)
        }
    }

    private fun drawKey(c: Canvas, k: Key) {
        val r = RectF(k.rect)
        r.inset(dp(2.5f), dp(3.5f))
        val pressed = k === pressedKey
        val bg: Int
        val fg: Int
        when (k.kind) {
            Kind.CHAR, Kind.SPACE -> { bg = if (pressed) pal.keyPressed else pal.key; fg = pal.kbText }
            Kind.ENTER -> { bg = if (pressed) pal.accentPressed else pal.accent; fg = pal.onAccent }
            else -> { bg = if (pressed) pal.funcPressed else pal.func; fg = pal.kbText }
        }
        fill.color = bg
        fill.alpha = 255
        val radius = if (k.kind == Kind.ENTER) r.height() / 2 else dp(8f)
        c.drawRoundRect(r, radius, radius, fill)

        val cx = r.centerX()
        val cy = r.centerY()
        val u = dp(9f)
        line.color = fg
        line.strokeWidth = dp(1.9f)
        when (k.kind) {
            Kind.CHAR -> {
                val isLetter = k.label[0].isLetter()
                drawCentered(c, shown(k), cx, cy, sp(if (isLetter) 24f else 22f), fg, 255)
            }
            Kind.SYMBOLS, Kind.SYMBOLS2, Kind.LETTERS -> drawCentered(c, k.label, cx, cy, sp(15f), fg, 255)
            Kind.SHIFT -> drawShift(c, cx, cy, u, fg)
            Kind.BACKSPACE -> drawBackspace(c, cx, cy, u)
            Kind.ENTER -> drawEnter(c, cx, cy, u)
            Kind.GLOBE -> drawGlobe(c, cx, cy, u)
            else -> {}
        }
    }

    private fun drawCentered(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, alpha: Int) {
        textP.textSize = size
        textP.color = color
        textP.alpha = alpha
        val fm = textP.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, textP)
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, u: Float, color: Int) {
        val p = Path().apply {
            moveTo(cx, cy - u)
            lineTo(cx + u, cy + 0.1f * u)
            lineTo(cx + 0.45f * u, cy + 0.1f * u)
            lineTo(cx + 0.45f * u, cy + 0.85f * u)
            lineTo(cx - 0.45f * u, cy + 0.85f * u)
            lineTo(cx - 0.45f * u, cy + 0.1f * u)
            lineTo(cx - u, cy + 0.1f * u)
            close()
        }
        if (shift == Shift.OFF) {
            line.style = Paint.Style.STROKE
            c.drawPath(p, line)
        } else {
            fill.color = color
            fill.alpha = 255
            c.drawPath(p, fill)
            if (shift == Shift.LOCK) {
                c.drawRect(cx - 0.45f * u, cy + 1.1f * u, cx + 0.45f * u, cy + 1.3f * u, fill)
            }
        }
    }

    private fun drawBackspace(c: Canvas, cx: Float, cy: Float, u: Float) {
        val p = Path().apply {
            moveTo(cx + 1.1f * u, cy - 0.75f * u)
            lineTo(cx - 0.4f * u, cy - 0.75f * u)
            lineTo(cx - 1.1f * u, cy)
            lineTo(cx - 0.4f * u, cy + 0.75f * u)
            lineTo(cx + 1.1f * u, cy + 0.75f * u)
            close()
        }
        c.drawPath(p, line)
        c.drawLine(cx - 0.1f * u, cy - 0.3f * u, cx + 0.6f * u, cy + 0.3f * u, line)
        c.drawLine(cx + 0.6f * u, cy - 0.3f * u, cx - 0.1f * u, cy + 0.3f * u, line)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float, u: Float) {
        line.color = pal.onAccent
        val p = Path().apply {
            moveTo(cx + 1.0f * u, cy - 0.8f * u)
            lineTo(cx + 1.0f * u, cy + 0.15f * u)
            lineTo(cx - 0.9f * u, cy + 0.15f * u)
        }
        c.drawPath(p, line)
        c.drawLine(cx - 0.9f * u, cy + 0.15f * u, cx - 0.25f * u, cy - 0.45f * u, line)
        c.drawLine(cx - 0.9f * u, cy + 0.15f * u, cx - 0.25f * u, cy + 0.75f * u, line)
    }

    private fun drawGlobe(c: Canvas, cx: Float, cy: Float, u: Float) {
        c.drawCircle(cx, cy, u, line)
        c.drawOval(RectF(cx - 0.45f * u, cy - u, cx + 0.45f * u, cy + u), line)
        c.drawLine(cx - u, cy, cx + u, cy, line)
        c.drawLine(cx, cy - u, cx, cy + u, line)
    }

    private fun drawPreview(c: Canvas) {
        val k = pressedKey ?: return
        if (k.kind != Kind.CHAR || altOpen || repeating) return
        val pw = k.rect.width() * 1.25f
        val ph = k.rect.height() * 1.15f
        val left = (k.rect.centerX() - pw / 2).coerceIn(dp(2f), width - pw - dp(2f))
        val top = max(0f, k.rect.top - ph - dp(2f))
        val r = RectF(left, top, left + pw, top + ph)
        fill.color = pal.popup
        fill.alpha = 255
        c.drawRoundRect(r, dp(12f), dp(12f), fill)
        drawCentered(c, shown(k), r.centerX(), r.centerY(), sp(30f), pal.kbText, 255)
    }

    private fun altLayout(k: Key): RectF {
        val cell = max(k.rect.width(), dp(42f))
        val total = cell * altKeys.size
        val left = (k.rect.centerX() - total / 2).coerceIn(dp(2f), max(dp(2f), width - total - dp(2f)))
        val h = dp(52f)
        val top = max(0f, k.rect.top - h - dp(4f))
        return RectF(left, top, left + total, top + h)
    }

    private fun drawAlts(c: Canvas) {
        val k = pressedKey ?: return
        if (!altOpen || altKeys.isEmpty()) return
        val box = altLayout(k)
        fill.color = pal.popup
        fill.alpha = 255
        c.drawRoundRect(box, dp(14f), dp(14f), fill)
        val cell = box.width() / altKeys.size
        altKeys.forEachIndexed { i, s ->
            val sel = i == altIndex
            if (sel) {
                fill.color = pal.accent
                val cr = RectF(box.left + i * cell, box.top, box.left + (i + 1) * cell, box.bottom)
                cr.inset(dp(3f), dp(3f))
                c.drawRoundRect(cr, dp(10f), dp(10f), fill)
            }
            drawCentered(c, s, box.left + (i + 0.5f) * cell, box.centerY(), sp(22f), if (sel) pal.onAccent else pal.kbText, 255)
        }
    }

    // ---- touch ----
    private fun keyAt(x: Float, y: Float): Key? {
        if (y < stripH) return stripKeys.firstOrNull { it.rect.contains(x, y) }
        for (row in rows) for (k in row) if (k.kind != Kind.GAP && k.rect.contains(x, y)) return k
        // Clamp touches in the gaps between rows/edges to the nearest key.
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (row in rows) for (k in row) {
            if (k.kind == Kind.GAP) continue
            val dx = max(max(k.rect.left - x, 0f), x - k.rect.right)
            val dy = max(max(k.rect.top - y, 0f), y - k.rect.bottom)
            val dist = dx * dx + dy * dy
            if (dist < bestD) { bestD = dist; best = k }
        }
        return if (bestD < dp(14f) * dp(14f)) best else null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginPress(e.getPointerId(0), e.x, e.y)
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast typing: finish the first key right away, then track the new finger.
                finishPress(true)
                val i = e.actionIndex
                beginPress(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activeId)
                if (i >= 0) movePress(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activeId) finishPress(true)
            }
            MotionEvent.ACTION_CANCEL -> finishPress(false)
        }
        return true
    }

    private fun haptic() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun beginPress(id: Int, x: Float, y: Float) {
        if (y < stripH && errorText != null) {
            host.dismissError()
            return
        }
        val k = keyAt(x, y) ?: return
        activeId = id
        pressedKey = k
        downX = x
        lastSpaceX = x
        longFired = false
        altOpen = false
        spaceDragging = false
        repeating = false
        haptic()
        when (k.kind) {
            Kind.BACKSPACE -> {
                host.backspace()
                repeating = true
                repeatCount = 0
                handler.postDelayed(backspaceTick, 400)
            }
            Kind.CHAR -> if (k.alts.isNotEmpty()) handler.postDelayed(longPress, 350)
            Kind.SPACE -> handler.postDelayed(longPress, 500)
            else -> {}
        }
        invalidate()
    }

    private fun movePress(x: Float, y: Float) {
        val k = pressedKey ?: return
        if (altOpen) {
            val box = altLayout(k)
            val cell = box.width() / altKeys.size
            altIndex = floor((x - box.left) / cell).toInt().coerceIn(0, altKeys.size - 1)
            invalidate()
            return
        }
        when (k.kind) {
            Kind.SPACE -> {
                if (abs(x - downX) > dp(14f)) {
                    spaceDragging = true
                    handler.removeCallbacks(longPress)
                }
                if (spaceDragging) {
                    val step = dp(14f)
                    while (abs(x - lastSpaceX) >= step) {
                        val dir = if (x > lastSpaceX) 1 else -1
                        host.moveCursor(dir)
                        lastSpaceX += dir * step
                    }
                }
            }
            Kind.CHAR -> {
                val n = keyAt(x, y)
                if (n != null && n !== k && n.kind == Kind.CHAR) {
                    handler.removeCallbacks(longPress)
                    pressedKey = n
                    if (n.alts.isNotEmpty()) handler.postDelayed(longPress, 350)
                    invalidate()
                }
            }
            else -> {}
        }
    }

    private fun onLongPress() {
        val k = pressedKey ?: return
        when (k.kind) {
            Kind.CHAR -> {
                if (k.alts.isEmpty()) return
                altKeys = k.alts.map { cased(it.toString()) }
                altIndex = 0
                altOpen = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
            Kind.SPACE -> {
                longFired = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                host.showPicker()
            }
            else -> {}
        }
    }

    private fun finishPress(commit: Boolean) {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(backspaceTick)
        val k = pressedKey
        if (commit && k != null) {
            when {
                altOpen -> {
                    host.commitText(altKeys[altIndex])
                    if (shift == Shift.ON) shift = Shift.OFF
                }
                longFired || spaceDragging || repeating -> {}
                else -> perform(k)
            }
        }
        pressedKey = null
        activeId = -1
        altOpen = false
        repeating = false
        spaceDragging = false
        longFired = false
        invalidate()
    }

    private fun perform(k: Key) {
        when (k.kind) {
            Kind.CHAR -> {
                host.commitText(shown(k))
                if (shift == Shift.ON && page == Page.LETTERS) shift = Shift.OFF
            }
            Kind.SPACE -> host.commitText(" ")
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val quick = now - lastShiftTap < 350
                shift = when (shift) {
                    Shift.OFF -> if (quick) Shift.LOCK else Shift.ON
                    Shift.ON -> if (quick) Shift.LOCK else Shift.OFF
                    Shift.LOCK -> Shift.OFF
                }
                lastShiftTap = now
            }
            Kind.SYMBOLS -> { page = Page.SYM1; layoutKeys() }
            Kind.SYMBOLS2 -> { page = Page.SYM2; layoutKeys() }
            Kind.LETTERS -> { page = Page.LETTERS; layoutKeys() }
            Kind.ENTER -> host.enter()
            Kind.GLOBE -> host.switchKeyboard()
            Kind.CHIP_TRANSLATE -> if (!translating) host.translate()
            Kind.CHIP_UNDO -> if (canUndo) host.undo()
            Kind.CHIP_PASTE -> host.paste()
            else -> {}
        }
    }

    companion object {
        private val ALTS = mapOf(
            "a" to "àáâäæãåā", "c" to "çćč", "e" to "èéêëēėę", "i" to "îïíīįì", "l" to "ł",
            "n" to "ñń", "o" to "ôöòóœøōõ", "s" to "ßśš", "u" to "ûüùúū", "y" to "ÿý", "z" to "žźż",
            "1" to "¹½⅓¼⅛", "2" to "²⅔", "3" to "³¾⅜", "4" to "⁴", "5" to "⁵⅝", "6" to "⁶",
            "7" to "⁷⅞", "8" to "⁸", "9" to "⁹", "0" to "⁰ⁿ∅",
            "." to ",?!:;-'\"()/@&_…",
            "-" to "_–—", "+" to "±", "(" to "<[{", ")" to ">]}", "/" to "\\÷",
            "\$" to "€£¥₹₨¢", "=" to "≠≈", "*" to "†‡★", "\"" to "“”„«»", "'" to "‘’‚",
            "?" to "¿", "!" to "¡", "%" to "‰",
        )
    }
}
