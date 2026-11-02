package com.paul.urdukb.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.urdukb.R

// Small building blocks shared by the app screens, so a screen reads as a list of what it shows, not how it is drawn.
// They follow the website: dark green cards with a hairline border, white pill buttons with a round dark badge, gold accents.

/** A rounded card with room around its content. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Brand.card,
        border = BorderStroke(1.dp, Brand.line),
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, color: Color = Brand.text) {
    Text(text, modifier = modifier.semantics { heading() }, color = color, fontSize = Type.title, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: TextUnit = Type.caption) {
    Text(text, modifier = modifier, color = Brand.muted, fontSize = size, lineHeight = size * 1.4f)
}

/** A small grey heading above a field or a row of buttons. */
@Composable
fun FieldLabel(text: String) = Muted(text, Modifier.padding(top = 14.dp, bottom = 6.dp))

/** The big heading of a screen or a setup step, the website's 64px line scaled down to a phone. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, modifier.fillMaxWidth().semantics { heading() }, color = Brand.text,
        fontSize = Type.headline, lineHeight = Type.headline * 1.1f, fontWeight = FontWeight.Medium, letterSpacing = (-0.8).sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** The grey line under a headline. */
@Composable
fun Lead(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, modifier.fillMaxWidth(), color = Brand.muted, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.45f,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** The website's small pill label above a section: a gold dot and a word. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Brand.raised).border(1.dp, Brand.line, RoundedCornerShape(50)).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Brand.gold2))
        Text(text, Modifier.padding(start = 7.dp), color = Brand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * A pill button. Filled is the main action: a white pill with dark text and, when [icon] is given, a round dark badge
 * holding the icon at the end (the website's download button). Unfilled is the quieter translucent one.
 * While [loading], the badge shows the Translate icon turning.
 */
@Composable
fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    icon: Int? = null,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val badge = icon != null || loading
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(start = if (badge) 18.dp else 12.dp, end = if (badge) 5.dp else 12.dp, top = 5.dp, bottom = 5.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) Brand.pillBg else Brand.raised,
            contentColor = if (filled) Brand.pillText else Brand.text,
            disabledContainerColor = if (filled) Brand.pillBg.copy(alpha = 0.55f) else Brand.raised.copy(alpha = 0.6f),
            disabledContentColor = if (filled) Brand.pillText.copy(alpha = 0.7f) else Brand.faint,
        ),
        border = if (filled) null else BorderStroke(1.dp, Brand.line),
    ) {
        Text(
            label, Modifier.weight(1f, fill = badge), fontSize = Type.body, fontWeight = FontWeight.Medium, maxLines = 2,
            textAlign = if (badge) TextAlign.Start else TextAlign.Center, lineHeight = Type.body * 1.25f,
        )
        if (badge) {
            Box(
                Modifier.padding(start = 10.dp).size(38.dp).clip(CircleShape).background(if (filled) Brand.pillIconBg else Brand.raised2),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) SpinningTranslate(18.dp, Brand.gold2)
                else Image(painterResource(icon!!), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(if (filled) Color.White else Brand.text))
            }
        }
    }
}

/** Buttons side by side, all as tall as the tallest, so a label that wraps never squashes its neighbour. */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** One choice out of several, shown as a row of chips with the chosen one white. */
@Composable
fun <T> Segmented(options: List<Pair<String, T>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Brand.pillBg else Brand.raised)
                    .border(1.dp, if (on) Brand.pillBg else Brand.line, RoundedCornerShape(50))
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) Brand.pillText else if (enabled) Brand.text else Brand.faint,
                    fontSize = Type.caption, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, lineHeight = Type.caption * 1.2f,
                )
            }
        }
    }
}

/** A filled text box with a hint. */
@Composable
fun TextBox(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    password: Boolean = false,
    sentences: Boolean = false,
    plain: Boolean = false,
    fontSize: TextUnit = Type.body,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(top = if (plain) 0.dp else 4.dp)
            .then(if (plain) Modifier else Modifier.border(1.dp, Brand.line, RoundedCornerShape(16.dp))),
        placeholder = { Text(hint, fontSize = fontSize, color = Brand.muted) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = fontSize, fontFamily = AppFont, lineHeight = fontSize * 1.4f),
        minLines = minLines,
        singleLine = minLines == 1 && !sentences,
        shape = RoundedCornerShape(if (plain) 0.dp else 16.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(capitalization = if (sentences) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = if (plain) Color.Transparent else Brand.raised,
            unfocusedContainerColor = if (plain) Color.Transparent else Brand.raised,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Brand.text,
            unfocusedTextColor = Brand.text,
            focusedPlaceholderColor = Brand.muted,
            unfocusedPlaceholderColor = Brand.muted,
            cursorColor = Brand.gold2,
        ),
    )
}

/** A setting with a switch on the right, and an optional line under the label. */
@Composable
fun SwitchRow(label: String, sub: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    // the whole row is the switch, so a screen reader reads the label with "on / off" and any part of the row can be tapped
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Brand.text, fontSize = Type.body)
            if (sub.isNotEmpty()) Muted(sub, size = Type.caption)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Brand.onGold, checkedTrackColor = Brand.gold, checkedBorderColor = Brand.gold,
                uncheckedThumbColor = Brand.muted, uncheckedTrackColor = Brand.raised, uncheckedBorderColor = Brand.line,
            ),
        )
    }
}

/** A row with a round icon badge, a title and a line under it. Used by setup, Learn and the Done step. */
@Composable
fun FeatureRow(res: Int, title: String, sub: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brand.raised).border(1.dp, Brand.line, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(res), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Brand.gold2))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Brand.text, fontSize = Type.bodyLarge, fontWeight = FontWeight.Medium)
            Muted(sub, Modifier.padding(top = 2.dp))
        }
    }
}

/** A numbered line: a gold circle with the number, then the text. */
@Composable
fun Numbered(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Brand.gold), contentAlignment = Alignment.Center) {
            Text("$n", color = Brand.onGold, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
        }
        Text(text, Modifier.weight(1f).padding(start = 14.dp), color = Brand.text, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.3f)
    }
}

/** A tinted icon. */
@Composable
fun TintedIcon(res: Int, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(res), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/**
 * The Translate icon turning on the spot, as the keyboard's own key does while the model works. It replaces the
 * loading circle everywhere a translation is in progress.
 */
@Composable
fun SpinningTranslate(size: Dp, tint: Color = Brand.gold2, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "translate-spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Image(
        painterResource(R.drawable.kb_translate), null,
        modifier.size(size).rotate(angle).clearAndSetSemantics {},
        colorFilter = ColorFilter.tint(tint),
    )
}

/** A line of text with the turning Translate icon in front, for "working" states. */
@Composable
fun Working(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.raised), contentAlignment = Alignment.Center) { SpinningTranslate(20.dp) }
        Text(text, Modifier.padding(start = 12.dp), color = Brand.text, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.35f)
    }
}

/**
 * The website's glow: soft gold, ember and green lights on the dark page. Drawn behind a hero area; it has no
 * semantics and takes no taps.
 */
@Composable
fun GlowBackdrop(modifier: Modifier = Modifier, strength: Float = 1f) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width; val h = size.height
        fun glow(cx: Float, cy: Float, r: Float, c: Color, a: Float) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a * strength), c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        }
        glow(w * 0.18f, h * 0.9f, w * 0.55f, Brand.gold, 0.55f)
        glow(w * 0.85f, h * 0.75f, w * 0.5f, Brand.ember, 0.45f)
        glow(w * 0.5f, h * 1.05f, w * 0.7f, Brand.green, 0.55f)
        glow(w * 0.5f, h * 0.35f, w * 0.35f, Brand.gold2, 0.22f)
    }
}

/** A thin divider in the card colour. */
@Composable
fun HairLine(modifier: Modifier = Modifier) = Spacer(modifier.fillMaxWidth().height(1.dp).background(Brand.line))

/** The round lantern-style app icon, as the website shows it. */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brand.raised), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_splash_lantern), null, Modifier.size(size * 1.55f))
    }
}

/** A small round tap target holding an icon, for toolbars. */
@Composable
fun IconChip(res: Int, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (selected) Brand.pillBg else Brand.raised)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { TintedIcon(res, if (selected) Brand.pillText else Brand.text, 20.dp) }
}

/** Filler to keep a Row's items apart. */
@Composable
fun RowScope.Gap(w: Dp) = Spacer(Modifier.width(w))
