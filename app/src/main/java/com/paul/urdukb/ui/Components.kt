package com.paul.urdukb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Small building blocks shared by the app screens, so a screen reads as a list of what it shows, not how it is drawn.

/** A rounded card with room around its content. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, modifier = modifier, color = color, fontSize = Type.bodyLarge, fontWeight = FontWeight.Medium)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: TextUnit = Type.caption) {
    Text(text, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = size, lineHeight = size * 1.3f)
}

/** A small grey heading above a field or a row of buttons. */
@Composable
fun FieldLabel(text: String) = Muted(text, Modifier.padding(top = 12.dp, bottom = 4.dp))

/** A pill button. Filled is the main action; unfilled is the quieter tonal one. */
@Composable
fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) c.primary else c.secondaryContainer,
            contentColor = if (filled) c.onPrimary else c.onSecondaryContainer,
            disabledContainerColor = (if (filled) c.primary else c.secondaryContainer).copy(alpha = 0.5f),
            disabledContentColor = (if (filled) c.onPrimary else c.onSecondaryContainer).copy(alpha = 0.7f),
        ),
    ) {
        Text(label, fontSize = Type.body, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 17.sp)
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

/** One choice out of several, shown as a row of pills with the chosen one filled. */
@Composable
fun <T> Segmented(options: List<Pair<String, T>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    ButtonRow {
        options.forEach { (label, value) ->
            PillButton(label, Modifier.weight(1f).fillMaxHeight(), filled = value == selected, enabled = enabled) { onSelect(value) }
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
    val c = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(top = if (plain) 0.dp else 4.dp),
        placeholder = { Text(hint, fontSize = fontSize) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = fontSize),
        minLines = minLines,
        singleLine = minLines == 1 && !sentences,
        shape = RoundedCornerShape(if (plain) 0.dp else 14.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(capitalization = if (sentences) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = if (plain) Color.Transparent else c.surfaceVariant,
            unfocusedContainerColor = if (plain) Color.Transparent else c.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = c.onSurface,
            unfocusedTextColor = c.onSurface,
            focusedPlaceholderColor = c.onSurfaceVariant,
            unfocusedPlaceholderColor = c.onSurfaceVariant,
        ),
    )
}

/** A setting with a switch on the right, and an optional line under the label. */
@Composable
fun SwitchRow(label: String, sub: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = Type.body)
            if (sub.isNotEmpty()) Muted(sub, size = Type.caption)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
