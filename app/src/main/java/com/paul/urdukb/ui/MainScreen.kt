package com.paul.urdukb.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.urdukb.BuildConfig
import com.paul.urdukb.KeyboardLook
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.Prefs
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
import java.io.File

/** What the Voice tab needs from the activity: permissions, sharing and the file picker live there. */
class VoiceActions(
    val onRecord: () -> Unit,
    val onShareAudio: (File) -> Unit,
    val onVoiceSettings: () -> Unit,
    val onPickAudio: () -> Unit,
)

/**
 * The app: four tabs behind a floating pill of tabs, like the website's nav. Translate holds the try box, Voice both
 * voice directions, Learn the how-to, and Settings the cards. What each card does lives in [MainModel].
 */
@Composable
fun MainScreen(model: MainModel, voice: VoiceModel, actions: VoiceActions, pickModelFile: () -> Unit, recreateScreen: () -> Unit) {
    val settingsList = rememberLazyListState()
    val s = model.snap

    LaunchedEffect(model.scrollRequest) {
        val target = model.scrollRequest ?: return@LaunchedEffect
        when (target) {
            Section.TRY -> model.tab = Tab.TRANSLATE
            Section.MODEL, Section.COLOUR, Section.GIPHY -> {
                model.tab = Tab.SETTINGS
                val offset = if (BuildConfig.PLAY) 0 else 1
                settingsList.animateScrollToItem(when (target) { Section.MODEL -> 2 + offset; Section.COLOUR -> 4 + offset; else -> 7 + offset })
            }
        }
        model.scrollRequest = null
    }

    // the Voice tab opens on "Speak English" the first time; after that it stays on whichever side was last used
    var voiceStarted by remember { mutableStateOf(false) }
    LaunchedEffect(model.tab) {
        if (model.tab == Tab.VOICE && !voiceStarted) { voiceStarted = true; voice.prepareSpeak() }
    }

    Box(Modifier.fillMaxSize().background(Brand.page)) {
        AnimatedContent(
            targetState = model.tab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { tab ->
            val top = WindowInsets.statusBars.union(WindowInsets.displayCutout)
            val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp)
            when (tab) {
                Tab.TRANSLATE -> LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(top).imePadding(), contentPadding = padding) { translateTab(model) }
                Tab.VOICE -> LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(top).imePadding(), contentPadding = padding) { voiceTab(model, voice, actions) }
                Tab.LEARN -> LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(top), contentPadding = padding) { learnTab(model) }
                Tab.SETTINGS -> LazyColumn(state = settingsList, modifier = Modifier.fillMaxSize().windowInsetsPadding(top).imePadding(), contentPadding = padding) {
                    item { TabHeader(stringResource(R.string.tab_settings), stringResource(R.string.settings_title), stringResource(R.string.settings_lead)) }
                    if (!BuildConfig.PLAY) item { UpdateCard(model) }
                    item { ChecklistCard(model) }
                    item { ModelCard(model, pickModelFile) }
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
        BottomTabs(model.tab, Modifier.align(Alignment.BottomCenter)) { model.tab = it }
    }
}

// ------------------------------------------------------------------ the tab bar and headers

@Composable
private fun BottomTabs(current: Tab, modifier: Modifier, onPick: (Tab) -> Unit) {
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .fillMaxWidth().height(64.dp).clip(RoundedCornerShape(50))
            .background(Brand.card.copy(alpha = 0.96f)).border(1.dp, Brand.line, RoundedCornerShape(50)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabItem(Tab.TRANSLATE, R.drawable.kb_translate, stringResource(R.string.tab_translate), current, onPick)
        TabItem(Tab.VOICE, R.drawable.kb_mic, stringResource(R.string.tab_voice), current, onPick)
        TabItem(Tab.LEARN, R.drawable.ic_tab_learn, stringResource(R.string.tab_learn), current, onPick)
        TabItem(Tab.SETTINGS, R.drawable.kb_settings, stringResource(R.string.tab_settings), current, onPick)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, icon: Int, label: String, current: Tab, onPick: (Tab) -> Unit) {
    val on = tab == current
    Row(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (on) Brand.pillBg else Color.Transparent)
            .selectable(selected = on, role = Role.Tab) { onPick(tab) }.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIcon(icon, if (on) Brand.pillText else Brand.muted, 18.dp)
        if (on) Text(label, Modifier.padding(start = 7.dp).clearAndSetSemantics {}, color = Brand.pillText, fontSize = Type.caption, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The top of a tab: a pill label, a headline and a grey line, on the website's glow. */
@Composable
fun TabHeader(label: String, title: String, lead: String, mark: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        GlowBackdrop(Modifier.matchParentSize(), strength = 0.55f)
        Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp)) {
            if (mark) AppMark(56.dp, Modifier.padding(bottom = 16.dp))
            SectionLabel(label)
            Headline(title, Modifier.padding(top = 14.dp))
            Lead(lead, Modifier.padding(top = 10.dp))
        }
    }
}

// ------------------------------------------------------------------ Translate tab

private fun LazyListScope.translateTab(model: MainModel) {
    item { TabHeader(stringResource(R.string.tab_translate), remember { Words.greeting() }, stringResource(R.string.header_tagline), mark = true) }
    item { ReadyStrip(model) }
    item { TryCard(model) }
    item { QuickTipsCard() }
}

/** One line on whether the translator is ready, with a jump to the model card. */
@Composable
private fun ReadyStrip(model: MainModel) {
    val s = model.snap
    val ok = s.statusTone == MainModel.Tone.OK
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(50)).background(Brand.card).border(1.dp, Brand.line, RoundedCornerShape(50))
            .clickable(role = Role.Button) { model.scrollRequest = Section.MODEL }.padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Brand.green2 else Brand.gold2))
        Text(s.status, Modifier.weight(1f).padding(start = 10.dp), color = Brand.text, fontSize = Type.caption, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(s.choice.label, color = Brand.muted, fontSize = Type.caption)
    }
}

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
                stringResource(R.string.style_formal) to Style.FORMAL.key,
            ),
            selected = s.toneChoice,
        ) { model.setToneChoice(it) }
        Segmented(
            listOf(
                stringResource(R.string.tone_poetic) to Style.POETIC.key, stringResource(R.string.tone_funny) to Style.FUNNY.key,
                stringResource(R.string.tone_respectful) to Style.RESPECTFUL.key,
            ),
            selected = s.toneChoice,
        ) { model.setToneChoice(it) }
        Muted(stringResource(R.string.try_tone_help), Modifier.padding(top = 8.dp), size = Type.caption)
        TextBox(
            model.tryInput, { model.tryInput = it }, stringResource(R.string.try_input_hint),
            modifier = Modifier.padding(top = 10.dp).focusRequester(focus), minLines = 3, sentences = true, fontSize = Type.bodyLarge,
        )
        ButtonRow {
            PillButton(
                stringResource(R.string.translate), Modifier.weight(1.4f).fillMaxHeight(), filled = true,
                enabled = !model.busy, icon = R.drawable.kb_translate, loading = model.translating,
            ) { model.tryTranslate() }
            if (model.tryHasResult) PillButton(stringResource(R.string.copy), Modifier.weight(1f).fillMaxHeight()) { model.copyOutput() }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Brand.raised).border(1.dp, Brand.line, RoundedCornerShape(16.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
            TextBox(
                model.tryOutput, { model.tryOutput = it }, stringResource(R.string.try_output_hint),
                minLines = 2, plain = true, fontSize = Type.title,
            )
        }
        if (model.canSaveExample) PillButton(stringResource(R.string.try_save_example), Modifier.fillMaxWidth().padding(top = 10.dp)) { model.saveExample() }
        if (model.tryNote.isNotEmpty()) Muted(model.tryNote, Modifier.padding(top = 8.dp), size = Type.caption)
    }
}

@Composable
private fun QuickTipsCard() {
    AppCard {
        CardTitle(stringResource(R.string.tips_title))
        FeatureRow(R.drawable.kb_translate, stringResource(R.string.tips_key_title), stringResource(R.string.tips_key_sub))
        FeatureRow(R.drawable.kb_undo, stringResource(R.string.tips_undo_title), stringResource(R.string.tips_undo_sub))
        FeatureRow(R.drawable.ic_setup_mood, stringResource(R.string.tips_tone_title), stringResource(R.string.tips_tone_sub))
    }
}

// ------------------------------------------------------------------ Settings: updates, checklist, model

@Composable
private fun UpdateCard(model: MainModel) {
    AppCard {
        CardTitle(stringResource(R.string.card_updates))
        Muted(model.updateText, Modifier.padding(top = 6.dp))
        PillButton(model.updateButton, Modifier.fillMaxWidth().padding(top = 10.dp), filled = model.updateIsNew) { model.onUpdateClick() }
    }
}

@Composable
private fun ChecklistCard(model: MainModel) {
    AppCard {
        CardTitle(stringResource(R.string.card_setup))
        model.snap.checks.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (c.done) Brand.green else Brand.raised).border(1.dp, if (c.done) Brand.green else Brand.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (c.done) "✓" else "${i + 1}", color = if (c.done) Color.White else Brand.text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(c.label, color = Brand.text.copy(alpha = if (c.done) 0.7f else 1f), fontSize = Type.body)
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
    val (okColor, errColor) = statusColors()
    AppCard {
        CardTitle(stringResource(R.string.card_model))
        Segmented(
            listOf(stringResource(R.string.model_choice_gemma4) to ModelChoice.GEMMA4, stringResource(R.string.model_choice_gemma3) to ModelChoice.GEMMA3)
                .filter { it.second in Prefs.offeredModels(LocalContext.current) },
            selected = s.choice,
        ) { model.chooseModel(it) }
        Muted(stringResource(R.string.model_choice_help), Modifier.padding(top = 8.dp), size = Type.caption)
        Text(
            s.status,
            Modifier.padding(top = 12.dp),
            color = when (s.statusTone) { MainModel.Tone.OK -> okColor; MainModel.Tone.ERROR -> errColor; else -> Brand.text },
            fontSize = Type.body, fontWeight = FontWeight.Medium,
        )
        Muted(s.detail)

        val transfer = model.transferText
        if (transfer != null || s.barProgress != null) {
            LinearProgressIndicator(
                progress = { if (transfer != null) model.transferProgress else (s.barProgress ?: 0f) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(50)),
                color = Brand.gold, trackColor = Brand.raised,
            )
        }
        if (transfer != null) Muted(transfer, Modifier.padding(top = 6.dp))

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

// ------------------------------------------------------------------ Settings: style, colour, speed

@Composable
private fun StyleCard(model: MainModel) {
    AppCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { model.styleOpen = !model.styleOpen }, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.card_style), Modifier.weight(1f))
            Text(stringResource(if (model.styleOpen) R.string.hide else R.string.edit), color = Brand.gold2, fontSize = Type.body, fontWeight = FontWeight.Medium)
        }
        Muted(stringResource(R.string.style_help))
        // who "I" is, so "I'm tired" becomes "Main thak gaya hoon" and never reads as "you"
        FieldLabel(stringResource(R.string.style_i_am))
        Segmented(listOf(stringResource(R.string.male) to Speaker.MALE, stringResource(R.string.female) to Speaker.FEMALE), selected = model.speaker) { model.chooseSpeaker(it) }
        Muted(stringResource(R.string.style_i_am_help), Modifier.padding(top = 8.dp))
        if (model.styleOpen) {
            Segmented(
                listOf(
                    stringResource(R.string.style_casual) to Style.CASUAL, stringResource(R.string.style_formal) to Style.FORMAL,
                    stringResource(R.string.style_poetic) to Style.POETIC,
                ),
                selected = model.styleSel,
            ) { model.showStyle(it) }
            Segmented(
                listOf(
                    stringResource(R.string.style_funny) to Style.FUNNY, stringResource(R.string.style_respectful) to Style.RESPECTFUL,
                    stringResource(R.string.dir_to_en) to Style.TO_ENGLISH,
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

/** The keyboard's colours. The app itself stays in its own green and gold. */
@Composable
private fun ColourCard(recreateScreen: () -> Unit) {
    val ctx = LocalContext.current
    val current = KeyboardLook.swatchId(ctx)
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
                            Modifier.size(46.dp).clip(CircleShape).background(fill).then(if (selected) Modifier.border(3.dp, Brand.gold2, CircleShape) else Modifier.border(1.dp, Brand.line, CircleShape)),
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
        Text(model.snap.stats, Modifier.padding(top = 8.dp), color = Brand.text, fontSize = Type.body, lineHeight = Type.body * 1.4f)
        SwitchRow(stringResource(R.string.speed_gpu), "", model.snap.gpu) { model.setGpu(it) }
        Muted(stringResource(R.string.speed_gpu_help))
        PillButton(stringResource(R.string.speed_reset), Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !model.busy) { model.resetStats() }
    }
}

// ------------------------------------------------------------------ Settings: keyboard, GIFs, credits, errors

@Composable
fun PrefSwitch(label: String, sub: String, flag: KbSettings.Flag) {
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
        PrefSwitch(stringResource(R.string.kb_smart_tone), stringResource(R.string.kb_smart_tone_sub), KbSettings.SMART_TONE)
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
            Text(linkify(text, Brand.gold2), Modifier.padding(top = 8.dp), color = Brand.muted, fontSize = Type.caption, lineHeight = Type.caption * 1.4f)
        }
        Muted(stringResource(R.string.version_line, BuildConfig.VERSION_NAME), Modifier.padding(top = 12.dp), size = Type.caption)
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
    AppCard {
        CardTitle(stringResource(R.string.card_error), color = Brand.error)
        SelectionContainer {
            Text(model.snap.error, Modifier.padding(vertical = 8.dp), color = Brand.text, fontSize = Type.caption, fontFamily = FontFamily.Monospace)
        }
        ButtonRow {
            PillButton(stringResource(R.string.copy), Modifier.weight(1f).fillMaxHeight(), filled = true) { model.copyError() }
            PillButton(stringResource(R.string.clear), Modifier.weight(1f).fillMaxHeight()) { model.clearError() }
        }
    }
}
