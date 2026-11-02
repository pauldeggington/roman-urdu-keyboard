package com.paul.urdukb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paul.urdukb.R

/** The words the gallery shows, so the same screen can be drawn in English and in Urdu. */
class GalleryText(
    val label: String, val title: String, val body: String, val field: String, val filled: String, val tonal: String, val off: String,
    val options: List<String>, val hint: String, val switchLabel: String, val switchSub: String, val working: String,
)

val englishGallery = GalleryText(
    label = "Keyboard", title = "Keyboard look", body = "Pick a colour for the keys. It also colours these screens.", field = "Colour",
    filled = "Turn on keyboard", tonal = "Skip", off = "Not ready", options = listOf("Light", "Dark", "Auto"),
    hint = "Type to try it", switchLabel = "Auto-correct", switchSub = "Fixes small spelling slips as you type", working = "Translating…",
)

val urduGallery = GalleryText(
    label = "کی بورڈ", title = "کی بورڈ کا انداز", body = "کیز کے لیے رنگ چنیں۔ یہ ان اسکرینوں کو بھی رنگ دیتا ہے۔", field = "رنگ",
    filled = "کی بورڈ آن کریں", tonal = "چھوڑیں", off = "تیار نہیں", options = listOf("روشن", "گہرا", "خودکار"),
    hint = "آزمانے کے لیے لکھیں", switchLabel = "خودکار درستی", switchSub = "ٹائپ کرتے وقت چھوٹی غلطیاں ٹھیک کرتا ہے", working = "ترجمہ ہو رہا ہے…",
)

/**
 * Every shared building block in one column. It is what the screenshot tests photograph and what Android Studio shows
 * as a preview, so a change to a component is seen at a glance in English, Urdu (right to left) and large text.
 */
@Composable
fun ComponentGallery(t: GalleryText = englishGallery) {
    Column(Modifier.fillMaxWidth().background(Brand.page).padding(16.dp)) {
        SectionLabel(t.label, Modifier.padding(bottom = 12.dp))
        Headline(t.title, Modifier.padding(bottom = 8.dp))
        Lead(t.body, Modifier.padding(bottom = 16.dp))
        AppCard {
            CardTitle(t.title)
            Muted(t.body, Modifier.padding(top = 4.dp))
            FieldLabel(t.field)
            Segmented(t.options.mapIndexed { i, s -> s to i }, selected = 0) {}
            ButtonRow {
                PillButton(t.filled, Modifier.weight(1f), filled = true, icon = R.drawable.kb_enter) {}
                PillButton(t.tonal, Modifier.weight(1f)) {}
            }
            ButtonRow { PillButton(t.off, Modifier.weight(1f), enabled = false) {} }
        }
        AppCard {
            SwitchRow(t.switchLabel, t.switchSub, checked = true) {}
            SwitchRow(t.switchLabel, "", checked = false) {}
            TextBox(value = "", onChange = {}, hint = t.hint)
            Numbered(1, t.body)
            FeatureRow(R.drawable.kb_translate, t.switchLabel, t.switchSub)
        }
    }
}

@Preview(name = "Gallery", showBackground = true, widthDp = 411)
@Composable
private fun GalleryPreview() = RomanUrduTheme { ComponentGallery() }

@Preview(name = "Gallery (Urdu)", showBackground = true, widthDp = 411, locale = "ur")
@Composable
private fun GalleryUrduPreview() = RomanUrduTheme { ComponentGallery(urduGallery) }

@Preview(name = "Gallery (large text)", showBackground = true, widthDp = 411, fontScale = 1.6f)
@Composable
private fun GalleryLargeTextPreview() = RomanUrduTheme { ComponentGallery() }
