package com.paul.urdukb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.urdukb.KeyboardLook
import com.paul.urdukb.LlmEngine
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.Prefs
import com.paul.urdukb.R
import com.paul.urdukb.RomanUrdu
import com.paul.urdukb.SetupActivity.Step
import com.paul.urdukb.Speaker
import com.paul.urdukb.ime.KbSettings
import kotlin.math.sin

/**
 * The first-run guide: one task per screen, gold step marks on top, one white pill button below, in the website's
 * green and gold. The small Gemma 3 translator is already inside the app, so the model step only offers Gemma 4.
 * What it does is in [SetupModel].
 */
@Composable
fun SetupScreen(model: SetupModel, pickModelFile: () -> Unit, recreateScreen: () -> Unit, closeGuide: () -> Unit) {
    val step = model.step
    val live = model.live
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    BackHandler(enabled = step.ordinal > 0 || model.problem != null) { if (!model.back()) closeGuide() }

    Box(Modifier.fillMaxSize().background(Brand.page)) {
        if (step == Step.WELCOME || step == Step.DONE) GlowBackdrop(Modifier.fillMaxWidth().height(420.dp).align(Alignment.TopCenter), strength = 0.9f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
            TopBar(model)
            if (live.chip.isNotEmpty()) {
                Text(
                    live.chip,
                    Modifier.padding(start = 24.dp, top = 4.dp).minimumInteractiveComponentSize().clip(RoundedCornerShape(50)).background(Brand.raised).border(1.dp, Brand.line, RoundedCornerShape(50))
                        .clickable(role = Role.Button) { model.show(Step.MODEL) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Brand.text, fontSize = Type.caption,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val problem = model.problem
                if (problem != null) {
                    Column(Modifier.verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Headline(stringResource(R.string.setup_problem_title), Modifier.padding(top = 16.dp))
                        Lead(stringResource(R.string.setup_problem_body), Modifier.padding(top = 10.dp))
                        SelectionContainer {
                            Text(problem, Modifier.padding(top = 14.dp), color = Brand.muted, fontSize = Type.caption, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 12 }) },
                        label = "step",
                    ) { s ->
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 12.dp)) {
                            when (s) {
                                Step.WELCOME -> Welcome()
                                Step.MODEL -> ModelStep(model)
                                Step.ENABLE -> EnableStep(model)
                                Step.SWITCH -> SwitchStep(model)
                                Step.PERSONAL -> PersonalStep(recreateScreen)
                                Step.PRACTICE -> PracticeStep(model)
                                Step.DONE -> DoneStep()
                            }
                        }
                    }
                }
            }
            BottomBar(model, pickModelFile)
        }
    }
}

// ------------------------------------------------------------------ top and bottom bars

@Composable
private fun TopBar(model: SetupModel) {
    val step = model.step
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val backVisible = step.ordinal > 0 && step != Step.DONE
        val back = stringResource(R.string.a11y_back)
        if (backVisible) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = back) { model.back() }.semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) {
                // the arrow points the way "back" is: right to left in English, left to right in Urdu
                Text(
                    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹",
                    Modifier.padding(bottom = 4.dp).clearAndSetSemantics {},
                    color = Brand.text, fontSize = Type.display, fontWeight = FontWeight.Medium,
                )
            }
        } else Spacer(Modifier.size(48.dp))
        val n = Step.entries.size
        val stepText = stringResource(R.string.a11y_step, step.ordinal + 1, n)
        Row(
            Modifier.weight(1f).padding(horizontal = 12.dp).height(4.dp).semantics(mergeDescendants = true) { contentDescription = stepText },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for (i in 0 until n) Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(if (i <= step.ordinal) Brand.gold else Brand.raised2))
        }
        Text(
            stringResource(R.string.setup_skip),
            Modifier.alpha(if (step == Step.DONE) 0f else 1f).minimumInteractiveComponentSize().clickable(enabled = step != Step.DONE, role = Role.Button) { model.finish(openApp = true) }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            color = Brand.muted, fontSize = Type.body, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BottomBar(model: SetupModel, pickModelFile: () -> Unit) {
    val step = model.step
    val live = model.live
    val ctx = LocalContext.current
    var secondaryLabel: String? = null
    var secondaryClick: () -> Unit = {}
    val primaryLabel: String
    val primaryClick: () -> Unit
    var primaryIcon: Int? = null
    when {
        model.problem != null -> {
            primaryLabel = stringResource(R.string.setup_skip_step); primaryClick = { model.skipProblem() }
            secondaryLabel = stringResource(R.string.setup_copy_details); secondaryClick = { model.copyProblem() }
        }
        step == Step.WELCOME -> { primaryLabel = stringResource(R.string.setup_get_started); primaryClick = { model.next() }; primaryIcon = R.drawable.kb_enter }
        step == Step.MODEL -> {
            val choice = Prefs.modelChoice(ctx)
            val gemma4Ready = choice == ModelChoice.GEMMA4 && LlmEngine.modelExists(ctx) && !live.modelLocked
            when {
                gemma4Ready -> { primaryLabel = stringResource(R.string.next); primaryClick = { model.next() } }
                live.modelLocked -> {
                    // downloading or checking: it carries on in the background while the guide continues
                    primaryLabel = stringResource(R.string.next); primaryClick = { model.next() }
                    if (live.secondary == SetupModel.Secondary.CANCEL_DOWNLOAD) { secondaryLabel = live.secondaryLabel; secondaryClick = { model.cancelDownload() } }
                }
                else -> {
                    primaryLabel = stringResource(R.string.setup_add_gemma4); primaryIcon = R.drawable.ic_setup_download
                    primaryClick = { if (choice != ModelChoice.GEMMA4) model.chooseModel(ModelChoice.GEMMA4); model.onModelPrimary() }
                    secondaryLabel = stringResource(R.string.setup_later)
                    secondaryClick = { model.next() }
                }
            }
        }
        step == Step.ENABLE -> {
            primaryLabel = stringResource(if (live.keyboardOn) R.string.next else R.string.setup_open_settings)
            primaryClick = { if (live.keyboardOn) model.next() else model.openKeyboardSettings() }
        }
        step == Step.SWITCH -> {
            primaryLabel = stringResource(if (live.keyboardCurrent) R.string.next else R.string.setup_choose_keyboard)
            primaryClick = { if (live.keyboardCurrent) model.next() else model.showPicker() }
        }
        step == Step.PERSONAL -> { primaryLabel = stringResource(R.string.next); primaryClick = { model.next() } }
        step == Step.PRACTICE -> {
            primaryLabel = stringResource(R.string.next); primaryClick = { model.next() }
            secondaryLabel = stringResource(R.string.setup_skip_for_now); secondaryClick = { model.next() }
        }
        else -> { primaryLabel = stringResource(R.string.setup_finish); primaryClick = { model.finish(openApp = true) }; primaryIcon = R.drawable.kb_done }
    }
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PillButton(primaryLabel, Modifier.fillMaxWidth().height(56.dp), filled = true, icon = primaryIcon, onClick = primaryClick)
        if (secondaryLabel != null) {
            Text(
                secondaryLabel,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { secondaryClick() }.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                color = Brand.muted, fontSize = Type.body, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
        }
        if (step == Step.MODEL && live.secondary == SetupModel.Secondary.IMPORT_FILE && !live.modelLocked) {
            Text(
                stringResource(R.string.setup_have_file),
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { pickModelFile() }.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Brand.faint, fontSize = Type.caption, textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------ steps

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Lantern(Modifier.padding(top = 28.dp))
        Headline(stringResource(R.string.setup_welcome_headline), Modifier.padding(top = 26.dp), center = true)
        Lead(stringResource(R.string.setup_welcome_body), Modifier.padding(top = 12.dp, bottom = 26.dp), center = true)
    }
    StepCard {
        FeatureRow(R.drawable.ic_setup_translate, stringResource(R.string.setup_feat_translate), stringResource(R.string.setup_feat_translate_sub))
        FeatureRow(R.drawable.ic_setup_offline, stringResource(R.string.setup_feat_private), stringResource(R.string.setup_feat_private_sub))
        FeatureRow(R.drawable.ic_setup_keyboard, stringResource(R.string.setup_feat_gboard), stringResource(R.string.setup_feat_gboard_sub))
    }
    Muted(stringResource(R.string.setup_welcome_note), Modifier.padding(top = 18.dp).fillMaxWidth())
}

/** The lantern mark with a slow breathing glow behind it. */
@Composable
private fun Lantern(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "lantern")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val s = 1f + 0.08f * sin(f * 2 * Math.PI).toFloat()
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).scale(s).clip(CircleShape).background(Brush.radialGradient(listOf(Brand.gold2.copy(alpha = 0.35f), Color.Transparent))).clearAndSetSemantics {})
        AppMark(104.dp)
    }
}

@Composable
private fun ModelStep(model: SetupModel) {
    val live = model.live
    val ctx = LocalContext.current
    val current = Prefs.modelChoice(ctx)
    SectionLabel(stringResource(R.string.setup_optional), Modifier.padding(top = 10.dp))
    Headline(stringResource(R.string.setup_model_title), Modifier.padding(top = 14.dp))
    Lead(stringResource(R.string.setup_model_body), Modifier.padding(top = 10.dp))

    Column(Modifier.padding(top = 18.dp)) {
        Prefs.offeredModels(ctx).sortedBy { it != ModelChoice.GEMMA4 }.forEach { m ->
            val selected = m == current
            val builtIn = m == ModelChoice.GEMMA3
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Brand.raised else Brand.card)
                    .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Brand.gold else Brand.line), RoundedCornerShape(20.dp))
                    .selectable(selected = selected, role = Role.RadioButton) { model.chooseModel(m) }.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (selected) Brand.gold else Brand.muted, CircleShape), contentAlignment = Alignment.Center) {
                        if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(Brand.gold))
                    }
                    Text(m.label, Modifier.weight(1f).padding(start = 10.dp), color = Brand.text, fontSize = Type.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(if (builtIn) stringResource(R.string.setup_built_in) else m.sizeText, color = if (builtIn) Brand.green2 else Brand.muted, fontSize = Type.caption, fontWeight = FontWeight.Medium)
                }
                Muted(
                    stringResource(if (m == ModelChoice.GEMMA4) R.string.setup_gemma4_sub else R.string.setup_gemma3_sub),
                    Modifier.padding(top = 8.dp, start = 28.dp),
                )
            }
        }
        if (live.modelNeedsToken) TokenHelp(model, current)
    }

    val (okColor, errColor) = statusColors()
    StepCard(Modifier.padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live.modelLocked) SpinningTranslate(20.dp, modifier = Modifier.padding(end = 10.dp))
            Text(
                live.modelLine, color = when (live.modelTone) { SetupModel.Tone.OK -> okColor; SetupModel.Tone.ERROR -> errColor; else -> Brand.text },
                fontSize = Type.body, fontWeight = FontWeight.Medium,
            )
        }
        live.modelBar?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(50)), color = Brand.gold, trackColor = Brand.raised) }
        Muted(live.modelNote, Modifier.padding(top = 8.dp))
    }

    // mobile data choice, only when on a metered network
    if (live.metered) {
        var wifiOnly by remember { mutableStateOf(model.wifiOnly) }
        SwitchRow(stringResource(R.string.setup_mobile_data), "", wifiOnly) { wifiOnly = it; model.wifiOnly = it }
    }
}

@Composable
private fun TokenHelp(model: SetupModel, m: ModelChoice) {
    StepCard(Modifier.padding(top = 10.dp)) {
        Muted(stringResource(R.string.setup_token_intro))
        Numbered(1, stringResource(R.string.setup_token_step1))
        Numbered(2, stringResource(R.string.setup_token_step2))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.setup_model_page), Modifier.weight(1f)) { model.openUrl(m.pageUrl) }
            PillButton(stringResource(R.string.setup_make_token), Modifier.weight(1f)) { model.openUrl("https://huggingface.co/settings/tokens") }
        }
        TextBox(model.token, { model.saveToken(it) }, stringResource(R.string.setup_token_hint), modifier = Modifier.padding(top = 8.dp), password = true)
    }
}

@Composable
private fun EnableStep(model: SetupModel) {
    StepArt(R.drawable.ic_setup_keyboard)
    Headline(stringResource(R.string.setup_enable_title), Modifier.padding(top = 18.dp))
    Lead(stringResource(R.string.setup_enable_body), Modifier.padding(top = 10.dp))
    Numbered(1, stringResource(R.string.setup_enable_1))
    Numbered(2, stringResource(R.string.setup_enable_2))
    Numbered(3, stringResource(R.string.setup_enable_3))
    ToggleIllustration()
    StepCard(Modifier.padding(top = 16.dp)) {
        Row {
            TintedIcon(R.drawable.ic_setup_lock, Brand.gold2, 22.dp)
            Muted(stringResource(R.string.setup_enable_note), Modifier.padding(start = 12.dp))
        }
    }
    if (model.live.keyboardOn) DoneBanner(stringResource(R.string.setup_enable_done))
}

@Composable
private fun SwitchStep(model: SetupModel) {
    StepArt(R.drawable.ic_setup_touch)
    Headline(stringResource(R.string.setup_switch_title), Modifier.padding(top = 18.dp))
    Lead(stringResource(R.string.setup_switch_body), Modifier.padding(top = 10.dp))
    PickerIllustration()
    Muted(stringResource(R.string.setup_switch_note), Modifier.padding(top = 14.dp))
    if (model.live.keyboardCurrent) DoneBanner(stringResource(R.string.setup_switch_done))
}

@Composable
private fun PersonalStep(recreateScreen: () -> Unit) {
    val ctx = LocalContext.current
    var speaker by remember { mutableStateOf(Speaker.get(ctx)) }
    var formal by remember { mutableStateOf(RomanUrdu.isFormal(ctx)) }
    StepArt(R.drawable.ic_setup_person)
    Headline(stringResource(R.string.setup_personal_title), Modifier.padding(top = 18.dp))
    Lead(stringResource(R.string.setup_personal_body), Modifier.padding(top = 10.dp))

    StepCard(Modifier.padding(top = 20.dp)) {
        Label(stringResource(R.string.style_i_am), first = true)
        Segmented(listOf(stringResource(R.string.male) to Speaker.MALE, stringResource(R.string.female) to Speaker.FEMALE), speaker) { speaker = it; Speaker.set(ctx, it) }
        Muted(stringResource(R.string.setup_personal_i_am_help), Modifier.padding(top = 8.dp))

        Label(stringResource(R.string.setup_how_talk))
        Segmented(listOf(stringResource(R.string.tone_casual) to false, stringResource(R.string.tone_formal) to true), formal) {
            formal = it
            KbSettings.of(ctx).formalTone = it
        }
        Muted(stringResource(R.string.setup_personal_tone_help), Modifier.padding(top = 8.dp))
    }

    StepCard(Modifier.padding(top = 12.dp)) {
        Label(stringResource(R.string.setup_theme), first = true)
        Segmented(
            listOf(stringResource(R.string.mode_follow) to KeyboardLook.MODE_FOLLOW, stringResource(R.string.mode_light) to KeyboardLook.MODE_LIGHT, stringResource(R.string.mode_dark) to KeyboardLook.MODE_DARK),
            KeyboardLook.mode(ctx),
        ) { KeyboardLook.apply(ctx, KeyboardLook.swatchId(ctx), it); recreateScreen() }

        Label(stringResource(R.string.setup_colour))
        val current = KeyboardLook.swatchId(ctx)
        KeyboardLook.SWATCHES.take(10).chunked(5).forEach { rowSw ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                rowSw.forEach { sw ->
                    val selected = sw.id == current
                    Box(
                        Modifier.weight(1f).height(52.dp)
                            .selectable(selected = selected, role = Role.RadioButton) { KeyboardLook.apply(ctx, sw.id, KeyboardLook.mode(ctx)); recreateScreen() }
                            .semantics { contentDescription = sw.name },
                        contentAlignment = Alignment.Center,
                    ) {
                        val fill = if (sw.id == KeyboardLook.WALLPAPER) Brush.linearGradient(listOf(Color(0xFFC27BA0), Color(0xFF6C7CE8), Color(0xFF2FB5A8))) else Brush.linearGradient(listOf(Color(sw.color), Color(sw.color)))
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(fill).then(if (selected) Modifier.border(3.dp, Brand.gold2, CircleShape) else Modifier.border(1.dp, Brand.line, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Text("✓", Modifier.clearAndSetSemantics {}, color = Color.White, fontSize = Type.bodyLarge) }
                    }
                }
            }
        }
        Muted(stringResource(R.string.setup_colour_note), Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PracticeStep(model: SetupModel) {
    val live = model.live
    val (okColor, _) = statusColors()
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
    Headline(stringResource(R.string.setup_practice_title), Modifier.padding(top = 16.dp))
    Lead(stringResource(R.string.setup_practice_body), Modifier.padding(top = 10.dp))
    ToolbarPicture(Modifier.padding(top = 16.dp))
    TextBox(
        text, { text = it }, stringResource(R.string.setup_practice_hint),
        modifier = Modifier.padding(top = 14.dp).focusRequester(focus), minLines = 3, sentences = true, fontSize = Type.bodyLarge,
    )
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live.practiceDone) TintedIcon(R.drawable.ic_setup_check, okColor, 20.dp, Modifier.padding(end = 8.dp))
        Text(live.practiceText, color = if (live.practiceDone) okColor else Brand.muted, fontSize = Type.caption, lineHeight = Type.caption * 1.4f)
    }
    StepCard(Modifier.padding(top = 16.dp)) {
        FeatureRow(R.drawable.kb_translate, stringResource(R.string.translate), stringResource(R.string.setup_tip_translate))
        FeatureRow(R.drawable.ic_setup_mood, stringResource(R.string.setup_tip_tone_title), stringResource(R.string.setup_tip_tone))
        FeatureRow(R.drawable.ic_setup_swap, stringResource(R.string.dir_auto), stringResource(R.string.setup_tip_auto))
    }
}

@Composable
private fun DoneStep() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Lantern(Modifier.padding(top = 28.dp))
        Headline(stringResource(R.string.setup_done_title), Modifier.padding(top = 26.dp), center = true)
        Lead(stringResource(R.string.setup_done_body), Modifier.padding(top = 12.dp, bottom = 26.dp), center = true)
    }
    StepCard {
        FeatureRow(R.drawable.ic_setup_translate, stringResource(R.string.translate), stringResource(R.string.setup_done_tip_translate))
        FeatureRow(R.drawable.ic_setup_person, stringResource(R.string.setup_done_tip_wrong), stringResource(R.string.setup_done_tip_wrong_sub))
        FeatureRow(R.drawable.kb_mic, stringResource(R.string.tab_voice), stringResource(R.string.setup_done_tip_voice))
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Label(text: String, first: Boolean = false) {
    Text(text, Modifier.padding(top = if (first) 0.dp else 20.dp, bottom = 4.dp), color = Brand.text, fontSize = Type.body, fontWeight = FontWeight.Medium)
}

@Composable
private fun StepCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.card).border(1.dp, Brand.line, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 16.dp)) { content() }
}

/** A step's picture: a big round badge with the icon, lit from behind. */
@Composable
private fun StepArt(res: Int) {
    Box(Modifier.padding(top = 12.dp).size(96.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Brand.gold.copy(alpha = 0.35f), Color.Transparent))).clearAndSetSemantics {})
        Box(Modifier.size(72.dp).clip(CircleShape).background(Brand.raised).border(1.dp, Brand.line, CircleShape), contentAlignment = Alignment.Center) {
            TintedIcon(res, Brand.gold2, 34.dp)
        }
    }
}

@Composable
private fun DoneBanner(text: String) {
    val (okColor, _) = statusColors()
    Row(
        Modifier.padding(top = 18.dp).clip(RoundedCornerShape(50)).background(Brand.raised).border(1.dp, Brand.line, RoundedCornerShape(50)).padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIcon(R.drawable.ic_setup_check, okColor, 22.dp)
        Text(text, Modifier.padding(start = 10.dp), color = Brand.text, fontSize = Type.body)
    }
}

/** A little settings row whose switch flips on and off, showing what to tap. */
@Composable
private fun ToggleIllustration() {
    val t = rememberInfiniteTransition(label = "toggle")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val on = f in 0.35f..0.9f
    val x = when {
        f < 0.35f -> 0f
        f < 0.45f -> (f - 0.35f) / 0.1f
        f < 0.9f -> 1f
        else -> 1f - (f - 0.9f) / 0.1f
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(20.dp)).background(Brand.page).border(1.dp, Brand.line, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(30.dp)
        Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 12.dp), color = Brand.text, fontSize = Type.body)
        Box(Modifier.width(52.dp).height(32.dp).clearAndSetSemantics {}.clip(RoundedCornerShape(16.dp)).background(if (on) Brand.gold else Brand.raised2)) {
            Box(
                Modifier.padding(start = 5.dp).align(Alignment.CenterStart).offset(x = (20f * x).dp).size(22.dp).clip(CircleShape)
                    .background(if (on) Brand.onGold else Brand.muted),
            )
        }
    }
}

/** Android's keyboard picker, drawn small, with this keyboard chosen. */
@Composable
private fun PickerIllustration() {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(20.dp)).background(Brand.page).border(1.dp, Brand.line, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp).clearAndSetSemantics {},
    ) {
        Text(stringResource(R.string.setup_picker_title), color = Brand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        PickerRow(stringResource(R.string.setup_picker_other), false)
        PickerRow(stringResource(R.string.app_name), true)
    }
}

@Composable
private fun PickerRow(name: String, on: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) Brand.gold else Brand.muted, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(Brand.gold))
        }
        Text(name, Modifier.padding(start = 12.dp), color = if (on) Brand.text else Brand.muted, fontSize = Type.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
    }
}
