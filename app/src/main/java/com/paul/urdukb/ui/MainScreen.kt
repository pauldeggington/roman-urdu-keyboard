package com.paul.urdukb.ui

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.urdukb.BuildConfig
import com.paul.urdukb.KeyboardLook
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.R
import com.paul.urdukb.RomanUrdu
import com.paul.urdukb.SetupActivity
import com.paul.urdukb.Speaker
import com.paul.urdukb.Style
import com.paul.urdukb.VariantMap
import com.paul.urdukb.Words
import com.paul.urdukb.ime.ClipStore
import com.paul.urdukb.ime.KbSettings
import com.paul.urdukb.ime.PersonalWords

/** The settings screen: a list of cards. What each card does lives in [MainModel]. */
@Composable
fun MainScreen(model: MainModel, pickModelFile: () -> Unit, recreateScreen: () -> Unit) {
    val listState = rememberLazyListState()
    val s = model.snap

    LaunchedEffect(model.scrollRequest) {
        val target = model.scrollRequest ?: return@LaunchedEffect
        val index = when (target) { Section.MODEL -> 3; Section.TRY -> 4; Section.COLOUR -> 6; Section.GIPHY -> 9 }
        listState.animateScrollToItem(index)
        model.scrollRequest = null
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
        ) {
            item { Header() }
            item { UpdateCard(model) }
            item { ChecklistCard(model) }
            item { ModelCard(model, pickModelFile) }
            item { TryCard(model) }
            item { StyleCard(model) }
            item { ColourCard(recreateScreen) }
            item { SpeedCard(model) }
            item { KeyboardCard(model) }
            item { GiphyCard() }
            item { CreditsCard() }
            if (s.error.isNotBlank()) item { ErrorCard(model) }
        }
    }
}

// ------------------------------------------------------------------ header and updates

@Composable
private fun Header() {
    val greeting = remember { Words.greeting() }
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        AndroidView(
            factory = { c -> ImageView(c).apply { setImageResource(R.mipmap.ic_launcher) } },
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(19.dp)),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(greeting, color = MaterialTheme.colorScheme.onBackground, fontSize = Type.title, fontWeight = FontWeight.Medium)
            Muted(stringResource(R.string.header_tagline), Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun UpdateCard(model: MainModel) {
    AppCard {
        CardTitle(stringResource(R.string.card_updates))
        Muted(model.updateText, Modifier.padding(top = 6.dp))
        PillButton(model.updateButton, Modifier.fillMaxWidth().padding(top = 10.dp), filled = model.updateIsNew) { model.onUpdateClick() }
    }
}

// ------------------------------------------------------------------ setup checklist and model

@Composable
private fun ChecklistCard(model: MainModel) {
    AppCard {
        CardTitle(stringResource(R.string.card_setup))
        model.snap.checks.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val cs = MaterialTheme.colorScheme
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (c.done) cs.primary else cs.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (c.done) "✓" else "${i + 1}", color = if (c.done) cs.onPrimary else cs.onSecondaryContainer, fontSize = Type.body, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(c.label, color = cs.onSurface.copy(alpha = if (c.done) 0.75f else 1f), fontSize = Type.body)
                    if (c.sub.isNotEmpty()) Muted(c.sub, size = Type.caption)
                }
                if (c.action != null && !c.done) PillButton(c.action, filled = true) { model.onCheckAction(i) }
            }
        }
    }
}

@Composable
private fun ModelCard(model: MainModel, pickModelFile: () -> Unit) {
    val s = model.snap
    val cs = MaterialTheme.colorScheme
    val (okColor, errColor) = statusColors()
    AppCard {
        CardTitle(stringResource(R.string.card_model))
        Segmented(
            listOf(stringResource(R.string.model_choice_gemma4) to ModelChoice.GEMMA4, stringResource(R.string.model_choice_gemma3) to ModelChoice.GEMMA3),
            selected = s.choice,
        ) { model.chooseModel(it) }
        Muted(stringResource(R.string.model_choice_help), Modifier.padding(top = 6.dp), size = Type.caption)
        Text(
            s.status,
            Modifier.padding(top = 8.dp),
            color = when (s.statusTone) { MainModel.Tone.OK -> okColor; MainModel.Tone.ERROR -> errColor; else -> cs.onSurface },
            fontSize = Type.body, fontWeight = FontWeight.Medium,
        )
        Muted(s.detail)

        val transfer = model.transferText
        if (transfer != null || s.barProgress != null) {
            LinearProgressIndicator(
                progress = { if (transfer != null) model.transferProgress else (s.barProgress ?: 0f) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = cs.primary,
            )
        }
        if (transfer != null) Muted(transfer)

        Muted(stringResource(R.string.model_background_note), Modifier.padding(top = 14.dp, bottom = 6.dp))
        if (s.choice.needsToken) {
            Muted(stringResource(R.string.model_token_help), size = Type.caption)
            TextBox(model.token, { model.token = it }, stringResource(R.string.model_token_hint), password = true)
        }
        ButtonRow {
            if (!s.downloading) PillButton(s.downloadLabel, Modifier.weight(1f).fillMaxHeight(), filled = true, enabled = !model.busy) { model.startDownload() }
            if (s.downloading || (model.busy && transfer != null)) PillButton(stringResource(R.string.cancel), Modifier.weight(1f).fillMaxHeight()) { model.cancelDownload() }
        }
        ButtonRow {
            PillButton(stringResource(R.string.model_use_file), Modifier.weight(1f).fillMaxHeight(), enabled = !model.busy) { pickModelFile() }
            if (s.showRemove) PillButton(stringResource(R.string.model_remove), Modifier.weight(1f).fillMaxHeight(), enabled = !model.busy) { model.removeModel() }
        }
        if (s.showVerify) PillButton(stringResource(R.string.model_verify), Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !model.busy) { model.runVerify() }
    }
}

// ------------------------------------------------------------------ Try it and Style

@Composable
private fun TryCard(model: MainModel) {
    val s = model.snap
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.focusTryBox) { if (model.focusTryBox > 0) runCatching { focus.requestFocus() } }
    AppCard {
        CardTitle(stringResource(R.string.card_try))
        FieldLabel(stringResource(R.string.try_direction))
        Segmented(
            listOf(
                stringResource(R.string.dir_auto) to RomanUrdu.DIR_AUTO,
                stringResource(R.string.dir_to_ru) to RomanUrdu.DIR_TO_RU,
                stringResource(R.string.dir_to_en) to RomanUrdu.DIR_TO_EN,
            ),
            selected = s.direction,
        ) { model.setDirection(it) }
        FieldLabel(stringResource(R.string.try_tone))
        Segmented(
            listOf(
                stringResource(R.string.tone_auto) to RomanUrdu.TONE_AUTO, stringResource(R.string.style_casual) to Style.CASUAL.key,
                stringResource(R.string.style_formal) to Style.FORMAL.key, stringResource(R.string.tone_poetic) to Style.POETIC.key,
            ),
            selected = s.toneChoice,
        ) { model.setToneChoice(it) }
        Segmented(
            listOf(
                stringResource(R.string.tone_funny) to Style.FUNNY.key, stringResource(R.string.tone_respectful) to Style.RESPECTFUL.key,
            ),
            selected = s.toneChoice,
        ) { model.setToneChoice(it) }
        Muted(stringResource(R.string.try_tone_help), Modifier.padding(top = 6.dp), size = Type.caption)
        TextBox(
            model.tryInput, { model.tryInput = it }, stringResource(R.string.try_input_hint),
            modifier = Modifier.padding(top = 8.dp).focusRequester(focus), minLines = 2, sentences = true,
        )
        ButtonRow {
            PillButton(stringResource(R.string.translate), Modifier.weight(1f).fillMaxHeight(), filled = true, enabled = !model.busy) { model.tryTranslate() }
            if (model.tryHasResult) PillButton(stringResource(R.string.copy), Modifier.weight(1f).fillMaxHeight()) { model.copyOutput() }
        }
        TextBox(
            model.tryOutput, { model.tryOutput = it }, stringResource(R.string.try_output_hint),
            modifier = Modifier.padding(top = 10.dp), minLines = 2, plain = true, fontSize = Type.bodyLarge,
        )
        if (model.canSaveExample) PillButton(stringResource(R.string.try_save_example), Modifier.fillMaxWidth().padding(top = 6.dp)) { model.saveExample() }
        if (model.tryNote.isNotEmpty()) Muted(model.tryNote, Modifier.padding(top = 4.dp), size = Type.caption)
    }
}

@Composable
private fun StyleCard(model: MainModel) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { model.styleOpen = !model.styleOpen }, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.card_style), Modifier.weight(1f))
            Text(stringResource(if (model.styleOpen) R.string.hide else R.string.edit), color = cs.primary, fontSize = Type.body)
        }
        Muted(stringResource(R.string.style_help))
        // who "I" is, so "I'm tired" becomes "Main thak gaya hoon" and never reads as "you"
        FieldLabel(stringResource(R.string.style_i_am))
        Segmented(listOf(stringResource(R.string.male) to Speaker.MALE, stringResource(R.string.female) to Speaker.FEMALE), selected = model.speaker) { model.chooseSpeaker(it) }
        Muted(stringResource(R.string.style_i_am_help), Modifier.padding(top = 6.dp))
        if (model.styleOpen) {
            Segmented(
                listOf(
                    stringResource(R.string.style_casual) to Style.CASUAL, stringResource(R.string.style_formal) to Style.FORMAL,
                    stringResource(R.string.style_poetic) to Style.POETIC, stringResource(R.string.style_funny) to Style.FUNNY,
                ),
                selected = model.styleSel,
            ) { model.showStyle(it) }
            Segmented(
                listOf(
                    stringResource(R.string.style_respectful) to Style.RESPECTFUL, stringResource(R.string.dir_to_en) to Style.TO_ENGLISH,
                ),
                selected = model.styleSel,
            ) { model.showStyle(it) }
            FieldLabel(stringResource(R.string.style_examples_label))
            TextBox(model.examples, { model.examples = it }, stringResource(R.string.style_examples_hint), minLines = 4)
            FieldLabel(stringResource(R.string.style_glossary_label))
            TextBox(model.glossary, { model.glossary = it }, "bada -> barha", minLines = 4)
            ButtonRow {
                PillButton(stringResource(R.string.save), Modifier.weight(1f).fillMaxHeight(), filled = true, enabled = !model.busy) { model.saveStyle() }
                PillButton(stringResource(R.string.style_reset_tab), Modifier.weight(1f).fillMaxHeight(), enabled = !model.busy) { model.resetStyle() }
            }
            ButtonRow { PillButton(stringResource(R.string.style_reset_glossary), Modifier.weight(1f).fillMaxHeight(), enabled = !model.busy) { model.resetGlossary() } }
        }
    }
}

// ------------------------------------------------------------------ colour, speed

@Composable
private fun ColourCard(recreateScreen: () -> Unit) {
    val ctx = LocalContext.current
    val current = KeyboardLook.swatchId(ctx)
    val outline = MaterialTheme.colorScheme.onSurface
    AppCard {
        CardTitle(stringResource(R.string.card_colour))
        Muted(stringResource(R.string.colour_help), Modifier.padding(top = 6.dp, bottom = 10.dp))
        KeyboardLook.SWATCHES.chunked(5).forEach { rowSwatches ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                rowSwatches.forEach { sw ->
                    val selected = sw.id == current
                    Column(
                        Modifier.weight(1f).selectable(selected = selected, role = Role.RadioButton) {
                            KeyboardLook.apply(ctx, sw.id, KeyboardLook.mode(ctx))
                            Toast.makeText(ctx, Words.colour(), Toast.LENGTH_SHORT).show()
                            recreateScreen()
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val fill = if (sw.id == KeyboardLook.WALLPAPER) Brush.linearGradient(listOf(Color(0xFFC27BA0), Color(0xFF6C7CE8), Color(0xFF2FB5A8))) else Brush.linearGradient(listOf(Color(sw.color), Color(sw.color)))
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(fill).then(if (selected) Modifier.border(3.dp, outline, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Text("✓", Modifier.clearAndSetSemantics {}, color = Color.White, fontSize = Type.bodyLarge, fontWeight = FontWeight.Bold) }
                        Muted(sw.name, Modifier.padding(top = 3.dp), size = Type.caption)
                    }
                }
            }
        }
        if (current != KeyboardLook.WALLPAPER) {
            FieldLabel(stringResource(R.string.colour_light_dark))
            Segmented(
                listOf(
                    stringResource(R.string.mode_follow) to KeyboardLook.MODE_FOLLOW,
                    stringResource(R.string.mode_light) to KeyboardLook.MODE_LIGHT,
                    stringResource(R.string.mode_dark) to KeyboardLook.MODE_DARK,
                ),
                selected = KeyboardLook.mode(ctx),
            ) {
                KeyboardLook.apply(ctx, KeyboardLook.swatchId(ctx), it)
                recreateScreen()
            }
        }
    }
}

@Composable
private fun SpeedCard(model: MainModel) {
    AppCard {
        CardTitle(stringResource(R.string.card_speed))
        Text(model.snap.stats, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = Type.body, lineHeight = Type.body * 1.4f)
        SwitchRow(stringResource(R.string.speed_gpu), "", model.snap.gpu) { model.setGpu(it) }
        Muted(stringResource(R.string.speed_gpu_help))
        PillButton(stringResource(R.string.speed_reset), Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !model.busy) { model.resetStats() }
    }
}

// ------------------------------------------------------------------ keyboard settings, GIFs, credits, errors

@Composable
private fun PrefSwitch(label: String, sub: String, flag: KbSettings.Flag) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(KbSettings.of(ctx)[flag]) }
    SwitchRow(label, sub, on) { v ->
        on = v
        // turning clipboard history off also deletes what was kept
        if (flag == KbSettings.CLIPBOARD_HISTORY) ClipStore.setEnabled(ctx, v) else KbSettings.of(ctx)[flag] = v
    }
}

@Composable
private fun KeyboardCard(model: MainModel) {
    val ctx = LocalContext.current
    var keepHours by remember { mutableStateOf(KbSettings.of(ctx).clipKeepHours) }
    AppCard {
        CardTitle(stringResource(R.string.card_keyboard))
        Muted(stringResource(R.string.kb_help), Modifier.padding(top = 6.dp))
        ButtonRow {
            PillButton(stringResource(R.string.kb_turn_on), Modifier.weight(1f).fillMaxHeight(), filled = true, enabled = !model.busy) { model.openKeyboardSettings() }
            PillButton(stringResource(R.string.kb_switch), Modifier.weight(1f).fillMaxHeight(), enabled = !model.busy) { model.showPicker() }
        }
        PrefSwitch(stringResource(R.string.kb_vibrate), "", KbSettings.VIBRATE)
        PrefSwitch(stringResource(R.string.kb_sound), "", KbSettings.SOUND)
        PrefSwitch(stringResource(R.string.kb_popup), stringResource(R.string.kb_popup_sub), KbSettings.KEY_PREVIEW)
        PrefSwitch(stringResource(R.string.kb_autocap), stringResource(R.string.kb_autocap_sub), KbSettings.AUTOCAP)
        PrefSwitch(stringResource(R.string.kb_warm), stringResource(R.string.kb_warm_sub), KbSettings.WARM_TRANSLATOR)
        PrefSwitch(stringResource(R.string.kb_suggest), stringResource(R.string.kb_suggest_sub), KbSettings.SUGGEST)
        PrefSwitch(stringResource(R.string.kb_autocorrect), stringResource(R.string.kb_autocorrect_sub), KbSettings.AUTOCORRECT)
        PillButton(stringResource(R.string.kb_forget_words), Modifier.fillMaxWidth().padding(top = 10.dp)) {
            PersonalWords.clear(ctx)
            Toast.makeText(ctx, ctx.getString(R.string.kb_words_cleared), Toast.LENGTH_SHORT).show()
        }
        PrefSwitch(stringResource(R.string.kb_glide), stringResource(R.string.kb_glide_sub), KbSettings.GLIDE)
        PrefSwitch(stringResource(R.string.kb_double_space), "", KbSettings.DOUBLE_SPACE)
        PrefSwitch(stringResource(R.string.kb_clipboard), stringResource(R.string.kb_clipboard_sub), KbSettings.CLIPBOARD_HISTORY)
        PillButton(stringResource(R.string.kb_clip_keep, ClipStore.ttlWords(ctx, keepHours)), Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val choices = KbSettings.CLIP_KEEP_CHOICES
            val next = choices[(choices.indexOf(keepHours) + 1) % choices.size]
            KbSettings.of(ctx).clipKeepHours = next
            keepHours = next
        }
        PrefSwitch(stringResource(R.string.kb_hints), stringResource(R.string.kb_hints_sub), KbSettings.NUMBER_HINTS)
        PillButton(stringResource(R.string.kb_run_setup), Modifier.fillMaxWidth().padding(top = 16.dp)) {
            SetupActivity.reset(ctx)
            ctx.startActivity(Intent(ctx, SetupActivity::class.java))
        }
    }
}

@Composable
private fun GiphyCard() {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(KbSettings.of(ctx).ownGiphyKey) }
    var has by remember { mutableStateOf(KbSettings.of(ctx).giphyKey.isNotEmpty()) }
    val builtIn = BuildConfig.GIPHY_KEY.isNotEmpty()
    AppCard {
        CardTitle(stringResource(R.string.card_giphy))
        Muted(
            stringResource(R.string.giphy_help) + "\n\n" + stringResource(if (builtIn) R.string.giphy_built_in else R.string.giphy_need_key_steps),
            Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        TextBox(key, { key = it }, stringResource(R.string.giphy_key_hint))
        ButtonRow {
            PillButton(stringResource(R.string.giphy_get_key), Modifier.weight(1f).fillMaxHeight()) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.giphy.com/dashboard/"))) }
            }
            PillButton(stringResource(R.string.save), Modifier.weight(1f).fillMaxHeight(), filled = true) {
                KbSettings.of(ctx).setGiphyKey(key)
                has = KbSettings.of(ctx).giphyKey.isNotEmpty()
                Toast.makeText(ctx, ctx.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
        }
        Muted(stringResource(if (has) R.string.giphy_on else R.string.giphy_off), Modifier.padding(top = 6.dp), size = Type.caption)
        Muted(stringResource(R.string.giphy_limits), Modifier.padding(top = 4.dp), size = Type.caption)
    }
}

@Composable
private fun CreditsCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val text = buildString {
        append(stringResource(R.string.credits_keyboard)).append("\n\n")
        append(stringResource(R.string.credits_translation)).append("\n\n")
        append(stringResource(R.string.credits_giphy)).append("\n\n")
        append(stringResource(R.string.credits_icons))
        if (VariantMap.available(ctx)) append("\n\n").append(stringResource(R.string.credits_data))
        append("\n\n").append(stringResource(R.string.credits_license))
    }
    AppCard {
        CardTitle(stringResource(R.string.card_credits))
        SelectionContainer {
            Text(linkify(text, cs.primary), Modifier.padding(top = 8.dp), color = cs.onSurfaceVariant, fontSize = Type.caption, lineHeight = Type.caption * 1.35f)
        }
    }
}

/** Web addresses in the text become tappable links. */
private fun linkify(text: String, color: Color) = buildAnnotatedString {
    val url = Regex("https?://[^\\s]+")
    var last = 0
    for (m in url.findAll(text)) {
        append(text.substring(last, m.range.first))
        val clean = m.value.trimEnd('.', ',', ')')
        withLink(LinkAnnotation.Url(clean, TextLinkStyles(SpanStyle(color = color)))) { append(clean) }
        last = m.range.first + clean.length
    }
    append(text.substring(last))
}

@Composable
private fun ErrorCard(model: MainModel) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        CardTitle(stringResource(R.string.card_error), color = cs.error)
        SelectionContainer {
            Text(model.snap.error, Modifier.padding(vertical = 8.dp), color = cs.onSurface, fontSize = Type.caption, fontFamily = FontFamily.Monospace)
        }
        ButtonRow {
            PillButton(stringResource(R.string.copy), Modifier.weight(1f).fillMaxHeight(), filled = true) { model.copyError() }
            PillButton(stringResource(R.string.clear), Modifier.weight(1f).fillMaxHeight()) { model.clearError() }
        }
    }
}
